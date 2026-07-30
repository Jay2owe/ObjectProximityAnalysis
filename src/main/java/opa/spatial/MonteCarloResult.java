/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.spatial;

import java.util.Arrays;

/**
 * Observed curve, complete-spatial-randomness expectation and Monte Carlo
 * envelope with a global maximum-deviation p-value.
 */
public final class MonteCarloResult {

    private final PatternFunction function;
    private final double[] radii;
    private final double[] observed;
    private final double[] expected;
    private final double[] lower;
    private final double[] upper;
    private final double globalPValue;
    private final double maximumDeviation;
    private final double maximumDeviationRadius;
    private final int simulations;
    private final long seed;

    MonteCarloResult(PatternFunction function,
                     double[] radii,
                     double[] observed,
                     double[] expected,
                     double[] lower,
                     double[] upper,
                     double globalPValue,
                     double maximumDeviation,
                     double maximumDeviationRadius,
                     int simulations,
                     long seed) {
        this.function = function;
        this.radii = copy(radii);
        this.observed = copy(observed);
        this.expected = copy(expected);
        this.lower = copy(lower);
        this.upper = copy(upper);
        this.globalPValue = globalPValue;
        this.maximumDeviation = maximumDeviation;
        this.maximumDeviationRadius = maximumDeviationRadius;
        this.simulations = simulations;
        this.seed = seed;
    }

    public PatternFunction getFunction() {
        return function;
    }

    public double[] getRadii() {
        return copy(radii);
    }

    public double[] getObserved() {
        return copy(observed);
    }

    public double[] getExpected() {
        return copy(expected);
    }

    public double[] getLower() {
        return copy(lower);
    }

    public double[] getUpper() {
        return copy(upper);
    }

    public double getGlobalPValue() {
        return globalPValue;
    }

    public double getMaximumDeviation() {
        return maximumDeviation;
    }

    public double getMaximumDeviationRadius() {
        return maximumDeviationRadius;
    }

    public int getSimulations() {
        return simulations;
    }

    public long getSeed() {
        return seed;
    }

    private static double[] copy(double[] values) {
        return Arrays.copyOf(values, values.length);
    }
}
