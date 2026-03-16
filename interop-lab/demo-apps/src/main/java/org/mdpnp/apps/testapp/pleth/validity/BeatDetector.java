package org.mdpnp.apps.testapp.pleth.validity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BeatDetector {

    private static final int REFRACTORY_PERIOD_SAMPLES = 25; // 250ms at 10Hz
    private static final int BEAT_WINDOW_SAMPLES = 10; // 1 second window for a beat

    private double[] templateBeat;

    public List<Integer> findPeaks(double[] data) {
        List<Integer> peaks = new ArrayList<>();
        double threshold = calculateAdaptiveThreshold(data);
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

    private double calculateAdaptiveThreshold(double[] data) {
        double max = 0;
        for (double d : data) {
            if (d > max) {
                max = d;
            }
        }
        return max * 0.5; // Threshold is 50% of the max peak in the window
    }

    public double[] calculateIBI(List<Integer> peaks, double sampleRate) {
        if (peaks.size() < 2) {
            return new double[0];
        }
        double[] ibis = new double[peaks.size() - 1];
        for (int i = 0; i < ibis.length; i++) {
            ibis[i] = (peaks.get(i + 1) - peaks.get(i)) / sampleRate;
        }
        return ibis;
    }

    public double calculatePulseRate(double[] ibis) {
        if (ibis.length == 0) {
            return 0;
        }
        double meanIbi = 0;
        for (double ibi : ibis) {
            meanIbi += ibi;
        }
        meanIbi /= ibis.length;
        return 60.0 / meanIbi;
    }

    public double calculateAmplitudeStability(List<Integer> peaks, double[] data) {
        if (peaks.size() < 2) {
            return 0.0;
        }
        double[] amplitudes = peaks.stream().mapToDouble(p -> data[p]).toArray();
        double mean = Arrays.stream(amplitudes).average().orElse(0);
        if (mean == 0) return 0.0;
        double stdDev = Math.sqrt(Arrays.stream(amplitudes).map(a -> Math.pow(a - mean, 2)).sum() / amplitudes.length);
        return 1.0 - (stdDev / mean); // Return as a stability metric (1.0 is perfect stability)
    }

    public double calculateIBIRegularity(double[] ibis) {
        if (ibis.length < 2) {
            return 0.0;
        }
        double mean = Arrays.stream(ibis).average().orElse(0);
        if (mean == 0) return 0.0;
        double stdDev = Math.sqrt(Arrays.stream(ibis).map(i -> Math.pow(i - mean, 2)).sum() / ibis.length);
        return 1.0 - (stdDev / mean); // Return as a stability metric
    }

    public double calculateNoiseIndex(double[] data) {
        double secondDerivativeSum = 0;
        for (int i = 1; i < data.length - 1; i++) {
            secondDerivativeSum += Math.abs(data[i+1] - 2 * data[i] + data[i-1]);
        }
        return secondDerivativeSum / data.length;
    }

    public void updateTemplate(double[] beat) {
        if (templateBeat == null) {
            templateBeat = Arrays.copyOf(beat, beat.length);
        } else {
            // Update with an exponential moving average
            for (int i = 0; i < templateBeat.length; i++) {
                templateBeat[i] = 0.9 * templateBeat[i] + 0.1 * beat[i];
            }
        }
    }

    public double calculateMorphologyCorrelation(List<Integer> peaks, double[] data) {
        if (templateBeat == null || peaks.isEmpty()) {
            return 0.0;
        }
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
        if (rawData == null || rawData.length == 0) {
            return 0.0;
        }
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0;
        for(double d : rawData) {
            if(d < min) min = d;
            if(d > max) max = d;
            sum += d;
        }
        double mean = sum / rawData.length;
        if(mean == 0) return 0.0;
        
        // PI = (AC / DC) * 100
        return ((max - min) / mean) * 100.0;
    }
}
