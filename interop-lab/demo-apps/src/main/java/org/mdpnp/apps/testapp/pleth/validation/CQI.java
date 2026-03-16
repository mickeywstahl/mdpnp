package org.mdpnp.apps.testapp.pleth.validation;

public class CQI {

    public enum Quality {
        HIGH, MEDIUM, LOW
    }

    private Quality currentQuality = Quality.LOW;
    private long lastHighTime = 0;
    private long lastLowTime = 0;

    public Quality calculateCQI(double amplitudeStability, double morphologyCorrelation, double noiseIndex, double regularity) {
        // Placeholder for CQI calculation
        double cqiScore = (amplitudeStability + morphologyCorrelation + regularity) - noiseIndex;

        if (cqiScore > 0.8) {
            lastHighTime = System.currentTimeMillis();
            if (currentQuality != Quality.HIGH && (lastHighTime - lastLowTime > 3000)) {
                currentQuality = Quality.HIGH;
            }
        } else if (cqiScore > 0.5) {
            if (currentQuality == Quality.HIGH) {
                // Tolerate brief dips to medium
            } else {
                currentQuality = Quality.MEDIUM;
            }
        } else {
            lastLowTime = System.currentTimeMillis();
            if (currentQuality != Quality.LOW && (lastLowTime - lastHighTime > 2000)) {
                currentQuality = Quality.LOW;
            }
        }
        return currentQuality;
    }
}
