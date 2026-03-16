package org.mdpnp.apps.testapp.pleth.validity;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

import org.mdpnp.apps.fxbeans.NumericFx;
import org.mdpnp.apps.fxbeans.NumericFxList;
import org.mdpnp.apps.fxbeans.SampleArrayFx;
import org.mdpnp.apps.fxbeans.SampleArrayFxList;
import org.mdpnp.apps.testapp.DeviceListModel;
import org.mdpnp.apps.testapp.pleth.validity.CQI.Quality;
import org.mdpnp.apps.testapp.pleth.validity.Filter.ButterworthBandpass;
import org.mdpnp.apps.testapp.pleth.validity.Resampler.DataPoint;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

public class PlethValidityApp implements Initializable {

    // FXML Fields
    @FXML private LineChart<Number, Number> plethChart;
    @FXML private LineChart<Number, Number> spo2Chart;
    @FXML private LineChart<Number, Number> validationChart;

    // Chart Series
    private XYChart.Series<Number, Number> plethSeries;
    private XYChart.Series<Number, Number> spo2Series;
    private XYChart.Series<Number, Number> validationSeries;

    // Data Models
    private DeviceListModel deviceListModel;
    private NumericFxList numericList;
    private SampleArrayFxList sampleList;

    // Pipeline Components
    private final Resampler resampler = new Resampler(15);
    private final ButterworthBandpass bandpassFilter = new ButterworthBandpass();
    private final SpO2RateLimiter spo2RateLimiter = new SpO2RateLimiter();
    private final BeatDetector beatDetector = new BeatDetector();
    private final CQI cqi = new CQI();
    
    // State Holders
    private Quality currentCqi = Quality.LOW;
    private double currentPi = 0.0;

    // --- Public API for Colleague ---
    private final DoubleProperty validatedSpO2 = new SimpleDoubleProperty(this, "validatedSpO2", 0.0);
    private final DoubleProperty validatedPulseRate = new SimpleDoubleProperty(this, "validatedPulseRate", 0.0);
    private final ObjectProperty<ValidationStatus> validationStatus = new SimpleObjectProperty<>(this, "validationStatus", ValidationStatus.INVALID);

    public DoubleProperty validatedSpO2Property() { return validatedSpO2; }
    public double getValidatedSpO2() { return validatedSpO2.get(); }
    public DoubleProperty validatedPulseRateProperty() { return validatedPulseRate; }
    public double getValidatedPulseRate() { return validatedPulseRate.get(); }
    public ObjectProperty<ValidationStatus> validationStatusProperty() { return validationStatus; }
    public ValidationStatus getValidationStatus() { return validationStatus.get(); }
    // --- End of Public API ---

    public enum ValidationStatus { 
        VALID(2), 
        LIMITED(1), 
        INVALID(0);
        
        private final int numericValue;

        ValidationStatus(int numericValue) {
            this.numericValue = numericValue;
        }

        public int getNumericValue() {
            return numericValue;
        }
    }

    private static final ArrayList<String> waveformIDs = new ArrayList<>(Arrays.asList(rosetta.MDC_PULS_OXIM_PLETH.VALUE));
    private static final ArrayList<String> numericIDs = new ArrayList<>(Arrays.asList(rosetta.MDC_PULS_OXIM_SAT_O2.VALUE));

    public void set(DeviceListModel deviceListModel, NumericFxList numericList, SampleArrayFxList sampleList) {
        this.deviceListModel = deviceListModel;
        this.numericList = numericList;
        this.sampleList = sampleList;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        plethSeries = new XYChart.Series<>();
        plethChart.getData().add(plethSeries);
        spo2Series = new XYChart.Series<>();
        spo2Chart.getData().add(spo2Series);
        validationSeries = new XYChart.Series<>();
        validationChart.getData().add(validationSeries);
    }

    public void start() {
        sampleList.addListener((ListChangeListener<SampleArrayFx>) c -> {
            while (c.next()) c.getAddedSubList().forEach(s -> {
                if (waveformIDs.contains(s.getMetric_id())) attachWaveformListener(s);
            });
        });
        numericList.addListener((ListChangeListener<NumericFx>) c -> {
            while (c.next()) c.getAddedSubList().forEach(n -> {
                if (numericIDs.contains(n.getMetric_id())) attachNumericListener(n);
            });
        });
    }

    private void attachWaveformListener(SampleArrayFx s) {
        s.presentation_timeProperty().addListener((obs, ov, nv) -> {
            for (Number value : s.getValues()) resampler.addSample(nv.getTime(), value.doubleValue());
            processPlethData(nv.getTime());
        });
    }

    private void attachNumericListener(NumericFx n) {
        n.presentation_timeProperty().addListener((obs, ov, nv) -> {
            double limitedSpo2 = spo2RateLimiter.limit(n.getValue(), nv.getTime());
            ValidationStatus status = validateSpO2(limitedSpo2);

            // Set the public properties for external listeners
            Platform.runLater(() -> {
                validationStatus.set(status);
                if (status != ValidationStatus.INVALID) {
                    validatedSpO2.set(limitedSpo2);
                    // Validated PR is set in processPlethData
                    spo2Series.getData().add(new XYChart.Data<>(nv.getTime(), limitedSpo2));
                }
                validationSeries.getData().add(new XYChart.Data<>(nv.getTime(), status.getNumericValue()));
            });
        });
    }

    private void processPlethData(long timestamp) {
        LinkedList<DataPoint> data = resampler.getResampledData();
        if (data.size() < 50) return;

        double[] rawWindow = data.subList(data.size() - 50, data.size()).stream().mapToDouble(d -> d.value).toArray();
        double[] filtered = bandpassFilter.filter(rawWindow);
        double[] detrended = Filter.detrend(filtered, 30);

        List<Integer> peaks = beatDetector.findPeaks(detrended);
        double[] ibis = beatDetector.calculateIBI(peaks, 10);
        double pulseRate = beatDetector.calculatePulseRate(ibis);

        double amplitudeStability = beatDetector.calculateAmplitudeStability(peaks, detrended);
        double regularity = beatDetector.calculateIBIRegularity(ibis);
        double noiseIndex = beatDetector.calculateNoiseIndex(rawWindow);
        double morphologyCorrelation = beatDetector.calculateMorphologyCorrelation(peaks, detrended);
        
        currentPi = BeatDetector.calculatePerfusionIndex(rawWindow);
        currentCqi = cqi.calculateCQI(amplitudeStability, morphologyCorrelation, noiseIndex, regularity);

        if (currentCqi == Quality.HIGH && !peaks.isEmpty()) {
            int lastPeak = peaks.get(peaks.size() - 1);
            if (lastPeak >= 5 && lastPeak < detrended.length - 5) {
                beatDetector.updateTemplate(Arrays.copyOfRange(detrended, lastPeak - 5, lastPeak + 5));
            }
        }
        
        // Set the public property for the pulse rate
        if (currentCqi != Quality.LOW && pulseRate >= 30 && pulseRate <= 220) {
             Platform.runLater(() -> validatedPulseRate.set(pulseRate));
        }

        Platform.runLater(() -> {
            plethSeries.getData().clear();
            for (int i = 0; i < detrended.length; i++) {
                plethSeries.getData().add(new XYChart.Data<>(timestamp - (50 - i) * 100, detrended[i]));
            }
        });
    }

    private ValidationStatus validateSpO2(double spo2) {
        if (spo2 < 70 || spo2 > 100) return ValidationStatus.INVALID;
        if (currentCqi == Quality.LOW || currentPi < 0.35) return ValidationStatus.INVALID;
        if (currentCqi == Quality.MEDIUM || currentPi < 0.5) return ValidationStatus.LIMITED;
        return ValidationStatus.VALID;
    }

    public void stop() { /* TODO */ }
}
