/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.measure.Calibration;

import java.util.Locale;

/**
 * Immutable voxel calibration used by all geometry calculations.
 */
public final class CalibrationInfo {

    private final double pixelWidth;
    private final double pixelHeight;
    private final double pixelDepth;
    private final String unit;
    private final boolean physicalUnits;

    private CalibrationInfo(double pixelWidth,
                            double pixelHeight,
                            double pixelDepth,
                            String unit,
                            boolean physicalUnits) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.pixelDepth = pixelDepth;
        this.unit = unit;
        this.physicalUnits = physicalUnits;
    }

    public static CalibrationInfo from(ImagePlus image) {
        if (image == null) {
            throw new IllegalArgumentException("Image must not be null.");
        }
        Calibration calibration = image.getCalibration();
        double width = positiveOrOne(calibration == null ? 1.0 : calibration.pixelWidth);
        double height = positiveOrOne(calibration == null ? 1.0 : calibration.pixelHeight);
        double depth = positiveOrOne(calibration == null ? 1.0 : calibration.pixelDepth);
        String rawUnit = calibration == null ? null : calibration.getUnit();
        String unit = normalizeUnit(rawUnit);
        boolean physical = !isPixelUnit(unit);
        return new CalibrationInfo(width, height, depth, unit, physical);
    }

    public double getPixelWidth() {
        return pixelWidth;
    }

    public double getPixelHeight() {
        return pixelHeight;
    }

    public double getPixelDepth() {
        return pixelDepth;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * True when the image declares a physical unit rather than pixels.
     */
    public boolean hasPhysicalUnits() {
        return physicalUnits;
    }

    public boolean isCompatibleWith(CalibrationInfo other) {
        if (other == null) return false;
        return nearlyEqual(pixelWidth, other.pixelWidth)
                && nearlyEqual(pixelHeight, other.pixelHeight)
                && nearlyEqual(pixelDepth, other.pixelDepth)
                && unit.equalsIgnoreCase(other.unit);
    }

    public double x(int pixelX) {
        return (pixelX + 0.5) * pixelWidth;
    }

    public double y(int pixelY) {
        return (pixelY + 0.5) * pixelHeight;
    }

    public double z(int pixelZ) {
        return (pixelZ + 0.5) * pixelDepth;
    }

    public int pixelX(double calibratedX) {
        return (int) Math.floor(calibratedX / pixelWidth);
    }

    public int pixelY(double calibratedY) {
        return (int) Math.floor(calibratedY / pixelHeight);
    }

    public int pixelZ(double calibratedZ) {
        return (int) Math.floor(calibratedZ / pixelDepth);
    }

    private static double positiveOrOne(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static String normalizeUnit(String value) {
        if (value == null || value.trim().isEmpty()) return "pixel";
        return value.trim();
    }

    private static boolean isPixelUnit(String value) {
        String unit = value.toLowerCase(Locale.ROOT);
        return unit.equals("pixel") || unit.equals("pixels") || unit.equals("px");
    }

    private static boolean nearlyEqual(double a, double b) {
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= 1.0e-9 * scale;
    }
}
