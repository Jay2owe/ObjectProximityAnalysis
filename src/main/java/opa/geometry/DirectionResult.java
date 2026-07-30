/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Measurements in one source-to-target direction.
 */
public final class DirectionResult {

    private final String sourceChannel;
    private final String targetChannel;
    private final boolean selfComparison;
    private final String unit;
    private final String surfaceMeasureUnit;
    private final List<ObjectMeasurement> measurements;

    DirectionResult(String sourceChannel,
                    String targetChannel,
                    boolean selfComparison,
                    String unit,
                    String surfaceMeasureUnit,
                    List<ObjectMeasurement> measurements) {
        this.sourceChannel = sourceChannel;
        this.targetChannel = targetChannel;
        this.selfComparison = selfComparison;
        this.unit = unit;
        this.surfaceMeasureUnit = surfaceMeasureUnit;
        this.measurements = Collections.unmodifiableList(
                new ArrayList<ObjectMeasurement>(measurements));
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public String getTargetChannel() {
        return targetChannel;
    }

    public boolean isSelfComparison() {
        return selfComparison;
    }

    public String getUnit() {
        return unit;
    }

    public String getSurfaceMeasureUnit() {
        return surfaceMeasureUnit;
    }

    public List<ObjectMeasurement> getMeasurements() {
        return measurements;
    }
}
