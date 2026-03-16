package org.mdpnp.apps.testapp.pleth.validity;

import java.util.LinkedList;

public class Resampler {
    private static final long RESAMPLE_INTERVAL_MS = 100; // 10 Hz
    private final LinkedList<DataPoint> resampledData = new LinkedList<>();
    private final int bufferSize;

    private long lastTimestamp = -1;
    private double lastValue = 0;

    public Resampler(int bufferDurationSeconds) {
        this.bufferSize = bufferDurationSeconds * 10; // 10 samples per second
    }

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
            if (resampledData.size() > bufferSize) {
                resampledData.removeFirst();
            }
            nextResampleTime += RESAMPLE_INTERVAL_MS;
        }

        lastTimestamp = timestamp;
        lastValue = value;
    }

    public LinkedList<DataPoint> getResampledData() {
        return resampledData;
    }

    public static class DataPoint {
        public final long timestamp;
        public final double value;

        public DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
