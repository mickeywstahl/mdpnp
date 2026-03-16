package org.mdpnp.apps.testapp.pleth.validation;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.Arrays;

public class BeatDetectorTest {

    @Test
    public void testPulseRatePlausibility() {
        // Test case 1: Valid pulse rate
        List<Integer> peaks1 = Arrays.asList(10, 20, 30, 40, 50);
        double[] ibis1 = BeatDetector.calculateIBI(peaks1, 10);
        double pr1 = BeatDetector.calculatePulseRate(ibis1);
        assertTrue(pr1 >= 30 && pr1 <= 220);

        // Test case 2: Invalid pulse rate (too low)
        List<Integer> peaks2 = Arrays.asList(10, 110, 210, 310, 410);
        double[] ibis2 = BeatDetector.calculateIBI(peaks2, 10);
        double pr2 = BeatDetector.calculatePulseRate(ibis2);
        assertFalse(pr2 >= 30 && pr2 <= 220);
    }
}
