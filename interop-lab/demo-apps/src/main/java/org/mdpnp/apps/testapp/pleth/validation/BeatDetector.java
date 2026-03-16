package org.mdpnp.apps.testapp.pleth.validation;

import java.util.ArrayList;
import java.util.List;

public class BeatDetector {

    private static final int REFRACTORY_PERIOD_SAMPLES = 25; // 250ms at 10Hz

    public static List<Integer> findPeaks(double[] data) {
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

    private static double calculateAdaptiveThreshold(double[] data) {
        double sum = 0;
        for (double d : data) {
            sum += d;
        }
        return (sum / data.length) * 1.5; // Simple adaptive threshold
    }

    public static double[] calculateIBI(List<Integer> peaks, double sampleRate) {
        if (peaks.size() < 2) {
            return new double[0];
        }
        double[] ibis = new double[peaks.size() - 1];
        for (int i = 0; i < ibis.length; i++) {
            ibis[i] = (peaks.get(i + 1) - peaks.get(i)) / sampleRate;
        }
        return ibis;
    }

    public static double calculatePulseRate(double[] ibis) {
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
    
    // Other feature computation methods will go here
}
