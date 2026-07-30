/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import opa.spatial.MonteCarloResult;

/**
 * One channel or directed channel-pair point-pattern result.
 */
public final class PatternResult {

    private final String sourceChannel;
    private final String targetChannel;
    private final String unit;
    private final MonteCarloResult statistics;

    PatternResult(String sourceChannel,
                  String targetChannel,
                  String unit,
                  MonteCarloResult statistics) {
        this.sourceChannel = sourceChannel;
        this.targetChannel = targetChannel;
        this.unit = unit;
        this.statistics = statistics;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public String getTargetChannel() {
        return targetChannel;
    }

    public boolean isBivariate() {
        return targetChannel != null;
    }

    public String getUnit() {
        return unit;
    }

    public MonteCarloResult getStatistics() {
        return statistics;
    }
}
