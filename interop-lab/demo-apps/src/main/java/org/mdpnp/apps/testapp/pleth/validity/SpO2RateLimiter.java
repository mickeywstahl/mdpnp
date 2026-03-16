package org.mdpnp.apps.testapp.pleth.validity;

public class SpO2RateLimiter {

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

        if (Math.abs(change) > maxChange) {
            spo2 = lastSpo2 + Math.signum(change) * maxChange;
        }

        lastSpo2 = spo2;
        lastTime = timestamp;
        return spo2;
    }
}
