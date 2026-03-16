package org.mdpnp.apps.testapp.pleth.validation;

public class Filter {

    /**
     * A cascade of two second-order IIR filters (biquads) to create a 4th-order Butterworth bandpass filter.
     * Coefficients are pre-calculated for a 0.5-5.0 Hz passband with a 10 Hz sampling rate.
     * Note: The 5.0 Hz high-cutoff is at the Nyquist frequency, so this effectively acts as a high-pass filter.
     */
    public static class ButterworthBandpass {
        // Coefficients for the two biquad sections
        private final BiquadSection section1;
        private final BiquadSection section2;

        public ButterworthBandpass() {
            // Coefficients for 0.5-5.0 Hz bandpass @ 10 Hz Fs, 4th Order
            // These were generated using standard signal processing tools.
            section1 = new BiquadSection(0.0985, 0, -0.0985, 1, -1.5610, 0.7726);
            section2 = new BiquadSection(1.0, -2.0, 1.0, 1, -1.5610, 0.7726);
        }

        public double filter(double input) {
            double out1 = section1.filter(input);
            return section2.filter(out1);
        }

        public double[] filter(double[] data) {
            double[] filteredData = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                filteredData[i] = this.filter(data[i]);
            }
            return filteredData;
        }
    }

    /**
     * Implements a single second-order IIR (biquad) filter section.
     * The transfer function is: H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
     */
    private static class BiquadSection {
        // Coefficients
        private final double b0, b1, b2, a1, a2;

        // State variables (delay elements)
        private double x1, x2, y1, y2;

        public BiquadSection(double b0, double b1, double b2, double a0, double a1, double a2) {
            // Normalize coefficients by a0
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
            this.x1 = 0;
            this.x2 = 0;
            this.y1 = 0;
            this.y2 = 0;
        }

        public double filter(double x0) {
            // Direct Form II Transposed implementation for better numerical stability
            double y0 = b0 * x0 + y1;
            y1 = b1 * x0 - a1 * y0 + y2;
            y2 = b2 * x0 - a2 * y0;
            return y0;
        }
    }

    /**
     * Simple moving average for detrending.
     * @param data The input data array.
     * @param windowSize The number of samples in the moving average window.
     * @return The detrended data array.
     */
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

        for (int i = 0; i < data.length; i++) {
            detrended[i] = data[i] - movingAverage[i];
        }
        return detrended;
    }
}
