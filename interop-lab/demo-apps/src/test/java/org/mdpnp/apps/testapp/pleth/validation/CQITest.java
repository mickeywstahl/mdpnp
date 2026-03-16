package org.mdpnp.apps.testapp.pleth.validation;

import org.junit.Test;
import static org.junit.Assert.*;

public class CQITest {

    @Test
    public void testHysteresis() throws InterruptedException {
        CQI cqi = new CQI();

        // Initial state is LOW
        assertEquals(CQI.Quality.LOW, cqi.calculateCQI(0.2, 0.2, 0.8, 0.2));

        // Move to HIGH
        Thread.sleep(3100);
        assertEquals(CQI.Quality.HIGH, cqi.calculateCQI(0.9, 0.9, 0.1, 0.9));

        // Dip to MEDIUM
        assertEquals(CQI.Quality.HIGH, cqi.calculateCQI(0.6, 0.6, 0.4, 0.6));
        
        // Move to LOW
        Thread.sleep(2100);
        assertEquals(CQI.Quality.LOW, cqi.calculateCQI(0.2, 0.2, 0.8, 0.2));
    }
}
