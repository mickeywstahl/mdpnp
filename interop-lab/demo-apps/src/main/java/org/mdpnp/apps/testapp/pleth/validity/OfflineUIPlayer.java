package org.mdpnp.apps.testapp.pleth.validity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A headless, offline playback tool for processing data from text files.
 * This class is self-contained and can be run independently. It does NOT launch a UI.
 */
public class OfflineUIPlayer {

    private static final String PLETH_DATA_FILE = "pleth.txt";
    private static final String SPO2_DATA_FILE = "spo2.txt";
    private static final String OUTPUT_FILE = "val_spo2.txt";

    public static void main(String[] args) {
        System.out.println("Starting offline validation processing...");

        // Initialize pipeline components
        Resampler resampler = new Resampler(15);
        ButterworthBandpass bandpassFilter = new ButterworthBandpass();
        BeatDetector beatDetector = new BeatDetector();
        CQI cqi = new CQI();
        SpO2RateLimiter spo2RateLimiter = new SpO2RateLimiter();

        try (BufferedReader plethReader = new BufferedReader(new FileReader(PLETH_DATA_FILE));
             BufferedReader spo2Reader = new BufferedReader(new FileReader(SPO2_DATA_FILE));
             BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {

            String spo2Line;
            long timestamp = 0;

            while ((spo2Line = spo2Reader.readLine()) != null) {
                // FIX: Split by whitespace and take the first part to handle multi-column/formatted files
                double spo2 = Double.parseDouble(spo2Line.trim().split("\\s+")[0]);

                for (int i = 0; i < 10; i++) {
                    String plethLine = plethReader.readLine();
                    if (plethLine == null) break;
                    // FIX: Split by whitespace and take the first part
                    double plethValue = Double.parseDouble(plethLine.trim().split("\\s+")[0]);
                    resampler.addSample(timestamp + (i * 100), plethValue);
                }

                LinkedList<Resampler.DataPoint> data = resampler.getResampledData();
                if (data.size() < 50) {
                    timestamp += 1000;
                    continue;
                }

                double[] rawWindow = data.subList(data.size() - 50, data.size()).stream().mapToDouble(d -> d.value).toArray();
                double[] filtered = bandpassFilter.filter(rawWindow);
                double[] detrended = Filter.detrend(filtered, 30);

                List<Integer> peaks = beatDetector.findPeaks(detrended);
                double[] ibis = beatDetector.calculateIBI(peaks, 10);

                double amplitudeStability = beatDetector.calculateAmplitudeStability(peaks, detrended);
                double regularity = beatDetector.calculateIBIRegularity(ibis);
                double noiseIndex = beatDetector.calculateNoiseIndex(rawWindow);
                double morphologyCorrelation = beatDetector.calculateMorphologyCorrelation(peaks, detrended);

                double currentPi = BeatDetector.calculatePerfusionIndex(rawWindow);
                CQI.Quality currentCqi = cqi.calculateCQI(amplitudeStability, morphologyCorrelation, noiseIndex, regularity);

                if (currentCqi == CQI.Quality.HIGH && !peaks.isEmpty()) {
                    int lastPeak = peaks.get(peaks.size() - 1);
                    if (lastPeak >= 5 && lastPeak < detrended.length - 5) {
                        beatDetector.updateTemplate(Arrays.copyOfRange(detrended, lastPeak - 5, lastPeak + 5));
                    }
                }

                double limitedSpo2 = spo2RateLimiter.limit(spo2, timestamp);
                ValidationStatus status = validateSpO2(limitedSpo2, currentCqi, currentPi);

                if (status != ValidationStatus.INVALID) {
                    writer.write(String.format("%.2f,%d%n", limitedSpo2, status.getNumericValue()));
                } else {
                    writer.write(String.format("0.00,%d%n", status.getNumericValue()));
                }

                timestamp += 1000;
            }
            System.out.println("Processing complete. Output written to " + OUTPUT_FILE);

        } catch (IOException | NumberFormatException e) {
            System.err.println("Error during file processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static ValidationStatus validateSpO2(double spo2, CQI.Quality cqi, double pi) {
        if (spo2 < 70 || spo2 > 100) return ValidationStatus.INVALID;
        if (cqi == CQI.Quality.LOW || pi < 0.35) return ValidationStatus.INVALID;
        if (cqi == CQI.Quality.MEDIUM || pi < 0.5) return ValidationStatus.LIMITED;
        return ValidationStatus.VALID;
    }

    // --- Nested Validation Classes ---

    public enum ValidationStatus {
        VALID(2), LIMITED(1), INVALID(0);
        private final int numericValue;
        ValidationStatus(int numericValue) { this.numericValue = numericValue; }
        public int getNumericValue() { return numericValue; }
    }

    public static class Resampler {
        private static final long RESAMPLE_INTERVAL_MS = 100;
        private final LinkedList<DataPoint> resampledData = new LinkedList<>();
        private final int bufferSize;
        private long lastTimestamp = -1;
        private double lastValue = 0;

        public Resampler(int bufferDurationSeconds) { this.bufferSize = bufferDurationSeconds * 10; }

        public void addSample(long timestamp, double value) {
            if (lastTimestamp == -1) {
                lastTimestamp = timestamp;
                lastValue = value;
                return;
            }
            long nextResampleTime = (lastTimestamp / RESAMPLE_INTERVAL_MS + 1) * RESAMPLE_INTERVAL_MS;
            while (nextResampleTime <= timestamp) {
                double interpolatedValue = lastValue + (value - lastValue) * (nextResampleTime - lastTimestamp) / (double) (timestamp - lastTimestamp);
                resampledData.add(new DataPoint(nextResampleTime, interpolatedValue));
                if (resampledData.size() > bufferSize) resampledData.removeFirst();
                nextResampleTime += RESAMPLE_INTERVAL_MS;
            }
            lastTimestamp = timestamp;
            lastValue = value;
        }

        public LinkedList<DataPoint> getResampledData() { return resampledData; }

        public static class DataPoint {
            public final long timestamp;
            public final double value;
            public DataPoint(long timestamp, double value) { this.timestamp = timestamp; this.value = value; }
        }
    }

    public static class Filter {
        public static double[] detrend(double[] data, int windowSize) {
            double[] detrended = new double[data.length];
            double[] movingAverage = new double[data.length];
            double sum = 0;
            for (int i = 0; i < data.length; i++) {
                sum += data[i];
                if (i >= windowSize) {
                    sum -= data[i - windowSize];
                    movingAverage[i] = sum / windowSize;
                } else {
                    movingAverage[i] = sum / (i + 1);
                }
            }
            for (int i = 0; i < data.length; i++) detrended[i] = data[i] - movingAverage[i];
            return detrended;
        }
    }

    public static class ButterworthBandpass {
        private final BiquadSection section1;
        private final BiquadSection section2;

        public ButterworthBandpass() {
            section1 = new BiquadSection(0.0985, 0, -0.0985, 1, -1.5610, 0.7726);
            section2 = new BiquadSection(1.0, -2.0, 1.0, 1, -1.5610, 0.7726);
        }

        public double[] filter(double[] data) {
            double[] filteredData = new double[data.length];
            for (int i = 0; i < data.length; i++) filteredData[i] = this.filter(data[i]);
            return filteredData;
        }
        
        public double filter(double input) {
            return section2.filter(section1.filter(input));
        }

        private static class BiquadSection {
            private final double b0, b1, b2, a1, a2;
            private double y1, y2;
            public BiquadSection(double b0, double b1, double b2, double a0, double a1, double a2) {
                this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0;
                this.a1 = a1 / a0; this.a2 = a2 / a0;
            }
            public double filter(double x0) {
                double y0 = b0 * x0 + y1;
                y1 = b1 * x0 - a1 * y0 + y2;
                y2 = b2 * x0 - a2 * y0;
                return y0;
            }
        }
    }

    public static class BeatDetector {
        private static final int REFRACTORY_PERIOD_SAMPLES = 25;
        private static final int BEAT_WINDOW_SAMPLES = 10;
        private double[] templateBeat;

        public List<Integer> findPeaks(double[] data) {
            List<Integer> peaks = new ArrayList<>();
            double max = 0;
            for (double d : data) if (d > max) max = d;
            double threshold = max * 0.5;
            int lastPeak = -REFRACTORY_PERIOD_SAMPLES;
            for (int i = 1; i < data.length - 1; i++) {
                if (data[i] > threshold && data[i] > data[i - 1] && data[i] > data[i + 1]) {
                    if (i - lastPeak > REFRACTORY_PERIOD_SAMPLES) {
                        peaks.add(i);
                        lastPeak = i;
                    }
                }
            }
            return peaks;
        }

        public double[] calculateIBI(List<Integer> peaks, double sampleRate) {
            if (peaks.size() < 2) return new double[0];
            double[] ibis = new double[peaks.size() - 1];
            for (int i = 0; i < ibis.length; i++) ibis[i] = (peaks.get(i + 1) - peaks.get(i)) / sampleRate;
            return ibis;
        }

        public double calculateAmplitudeStability(List<Integer> peaks, double[] data) {
            if (peaks.size() < 2) return 0.0;
            double[] amplitudes = peaks.stream().mapToDouble(p -> data[p]).toArray();
            double mean = Arrays.stream(amplitudes).average().orElse(0);
            if (mean == 0) return 0.0;
            double stdDev = Math.sqrt(Arrays.stream(amplitudes).map(a -> Math.pow(a - mean, 2)).sum() / amplitudes.length);
            return 1.0 - (stdDev / mean);
        }

        public double calculateIBIRegularity(double[] ibis) {
            if (ibis.length < 2) return 0.0;
            double mean = Arrays.stream(ibis).average().orElse(0);
            if (mean == 0) return 0.0;
            double stdDev = Math.sqrt(Arrays.stream(ibis).map(i -> Math.pow(i - mean, 2)).sum() / ibis.length);
            return 1.0 - (stdDev / mean);
        }

        public double calculateNoiseIndex(double[] data) {
            double secondDerivativeSum = 0;
            for (int i = 1; i < data.length - 1; i++) secondDerivativeSum += Math.abs(data[i+1] - 2 * data[i] + data[i-1]);
            return secondDerivativeSum / data.length;
        }

        public void updateTemplate(double[] beat) {
            if (templateBeat == null) templateBeat = Arrays.copyOf(beat, beat.length);
            else for (int i = 0; i < templateBeat.length; i++) templateBeat[i] = 0.9 * templateBeat[i] + 0.1 * beat[i];
        }

        public double calculateMorphologyCorrelation(List<Integer> peaks, double[] data) {
            if (templateBeat == null || peaks.isEmpty()) return 0.0;
            double totalCorrelation = 0;
            int validBeats = 0;
            for (int peak : peaks) {
                if (peak >= BEAT_WINDOW_SAMPLES / 2 && peak < data.length - BEAT_WINDOW_SAMPLES / 2) {
                    double[] beat = Arrays.copyOfRange(data, peak - BEAT_WINDOW_SAMPLES / 2, peak + BEAT_WINDOW_SAMPLES / 2);
                    totalCorrelation += pearsonCorrelation(beat, templateBeat);
                    validBeats++;
                }
            }
            return validBeats > 0 ? totalCorrelation / validBeats : 0.0;
        }

        private double pearsonCorrelation(double[] x, double[] y) {
            double xMean = Arrays.stream(x).average().orElse(0);
            double yMean = Arrays.stream(y).average().orElse(0);
            double cov = 0, xStdDev = 0, yStdDev = 0;
            for (int i = 0; i < x.length; i++) {
                cov += (x[i] - xMean) * (y[i] - yMean);
                xStdDev += Math.pow(x[i] - xMean, 2);
                yStdDev += Math.pow(y[i] - yMean, 2);
            }
            double denominator = Math.sqrt(xStdDev * yStdDev);
            return denominator == 0 ? 0 : cov / denominator;
        }

        public static double calculatePerfusionIndex(double[] rawData) {
            if (rawData == null || rawData.length == 0) return 0.0;
            double min = Double.MAX_VALUE, max = Double.MIN_VALUE, sum = 0;
            for(double d : rawData) {
                if(d < min) min = d;
                if(d > max) max = d;
                sum += d;
            }
            double mean = sum / rawData.length;
            return (mean == 0) ? 0.0 : ((max - min) / mean) * 100.0;
        }
    }

    public static class CQI {
        public enum Quality { HIGH, MEDIUM, LOW }
        private Quality currentQuality = Quality.LOW;
        private long lastHighTime = 0;
        private long lastLowTime = 0;

        public Quality calculateCQI(double amplitudeStability, double morphologyCorrelation, double noiseIndex, double regularity) {
            double cqiScore = (amplitudeStability + morphologyCorrelation + regularity) / 3.0 - (noiseIndex * 0.5);
            if (cqiScore > 0.7) {
                if (currentQuality != Quality.HIGH && (System.currentTimeMillis() - lastLowTime > 3000)) currentQuality = Quality.HIGH;
                lastHighTime = System.currentTimeMillis();
            } else if (cqiScore > 0.4) {
                if (currentQuality == Quality.HIGH) { /* Tolerate brief dips */ }
                else currentQuality = Quality.MEDIUM;
            } else {
                if (currentQuality != Quality.LOW && (System.currentTimeMillis() - lastHighTime > 2000)) currentQuality = Quality.LOW;
                lastLowTime = System.currentTimeMillis();
            }
            return currentQuality;
        }
    }

    public static class SpO2RateLimiter {
        private static final double MAX_CHANGE_PER_SECOND = 0.4;
        private double lastSpo2 = -1;
        private long lastTime = -1;

        public double limit(double spo2, long timestamp) {
            if (lastSpo2 == -1) {
                lastSpo2 = spo2;
                lastTime = timestamp;
                return spo2;
            }
            long timeDiff = timestamp - lastTime;
            double maxChange = MAX_CHANGE_PER_SECOND * (timeDiff / 1000.0);
            double change = spo2 - lastSpo2;
            if (Math.abs(change) > maxChange) spo2 = lastSpo2 + Math.signum(change) * maxChange;
            lastSpo2 = spo2;
            lastTime = timestamp;
            return spo2;
        }
    }
}
