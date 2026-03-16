package org.mdpnp.apps.testapp.pleth.validation;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpO2RateLimiterTest {

    @Test
    public void testSuddenDrop() {
        SpO2RateLimiter limiter = new SpO2RateLimiter();
        double initialSpo2 = 96.0;
        long initialTime = System.currentTimeMillis();

        limiter.limit(initialSpo2, initialTime);

        double newSpo2 = 85.0;
        long newTime = initialTime + 1000; // 1 second later

        double limitedSpo2 = limiter.limit(newSpo2, newTime);
        assertEquals(95.6, limitedSpo2, 0.01);
    }

    @Test
    public void testGradualChange() {
        SpO2RateLimiter limiter = new SpO2RateLimiter();
        double initialSpo2 = 96.0;
        long initialTime = System.currentTimeMillis();

        limiter.limit(initialSpo2, initialTime);

        double newSpo2 = 92.0;
        long newTime = initialTime + 10000; // 10 seconds later

        double limitedSpo2 = limiter.limit(newSpo2, newTime);
        assertEquals(92.0, limitedSpo2, 0.01);
    }
}
