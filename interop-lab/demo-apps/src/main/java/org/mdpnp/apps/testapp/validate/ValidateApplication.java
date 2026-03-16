package org.mdpnp.apps.testapp.validate;

import java.util.concurrent.ScheduledExecutorService;
import org.mdpnp.apps.testapp.vital.VitalModel;

public class ValidateApplication {

    public ValidateApplication() {
    }

    public void setModel(VitalModel vitalModel, ScheduledExecutorService executor, ValidationOracle validationOracle) {
        // Hollow shell: no data processing, DDS reading, or charting logic
    }
}
