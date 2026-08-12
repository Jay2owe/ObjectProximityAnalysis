/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.CalibrationInfo;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ROI-set conversion utilities adapted from the CPC plugin chassis.
 */
public final class LabelUtils {

    private LabelUtils() {
    }

    public static Roi[] loadRoiSet(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("ROI-set path must not be empty.");
        }
        if (path.toLowerCase().endsWith(".roi")) {
            Roi roi = new RoiDecoder(path).getRoi();
            return roi == null ? new Roi[0] : new Roi[]{roi};
        }

        List<Roi> rois = new ArrayList<Roi>();
        byte[] buffer = new byte[65536];
        ZipInputStream input = new ZipInputStream(new FileInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.getName().toLowerCase().endsWith(".roi")) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) > 0) {
                    bytes.write(buffer, 0, count);
                }
                Roi roi = new RoiDecoder(
                        bytes.toByteArray(), entry.getName()).getRoi();
                if (roi != null) rois.add(roi);
            }
        } finally {
            input.close();
        }
        return rois.toArray(new Roi[rois.size()]);
    }

    /**
     * Converts one ROI per object to a 16-bit label image.
     */
    public static ImagePlus roiSetToLabelImage(ImagePlus reference, Roi[] rois) {
        if (reference == null || reference.getStack() == null) {
            throw new IllegalArgumentException(
                    "A reference image is required for ROI dimensions and calibration.");
        }
        if (rois == null) {
            throw new IllegalArgumentException("ROI array must not be null.");
        }
        if (rois.length > 65535) {
            throw new IllegalArgumentException(
                    "A 16-bit ROI label image supports at most 65,535 objects.");
        }

        int width = reference.getWidth();
        int height = reference.getHeight();
        int slices = Math.max(1, reference.getNSlices());
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < slices; z++) {
            stack.addSlice(new ShortProcessor(width, height));
        }

        for (int index = 0; index < rois.length; index++) {
            Roi roi = rois[index];
            int label = index + 1;
            if (roi == null) {
                throw new IllegalArgumentException(
                        "ROI " + label + " is null.");
            }
            if (!roi.isArea()) {
                throw new IllegalArgumentException(
                        "ROI " + label
                                + " is not an area selection and cannot define "
                                + "an object label.");
            }
            if (roi.getCPosition() > 1 || roi.getTPosition() > 1) {
                throw new IllegalArgumentException(
                        "ROI " + label
                                + " targets an unsupported channel or time "
                                + "position; object ROIs must use C/T 0 or 1.");
            }
            int zPosition = roi.getZPosition();
            if (zPosition > slices) {
                throw new IllegalArgumentException(
                        "ROI " + label + " has Z position " + zPosition
                                + " outside the reference stack (1-"
                                + slices + ").");
            }
            int painted = 0;
            if (zPosition > 0) {
                painted += fill(
                        stack.getProcessor(zPosition), roi, label);
            } else {
                for (int z = 1; z <= slices; z++) {
                    painted += fill(stack.getProcessor(z), roi, label);
                }
            }
            if (painted == 0) {
                throw new IllegalArgumentException(
                        "ROI " + label
                                + " does not cover any in-bounds image pixels.");
            }
        }

        ImagePlus result = new ImagePlus("Labels from ROIs", stack);
        if (reference.getCalibration() != null) {
            result.setCalibration(reference.getCalibration().copy());
        }
        return result;
    }

    public static RectangularWindow boundingWindow(ImagePlus reference,
                                                   Roi[] regionRois) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference image must not be null.");
        }
        if (regionRois == null || regionRois.length == 0) {
            throw new IllegalArgumentException(
                    "At least one observation-region ROI is required.");
        }
        Rectangle bounds = null;
        for (int index = 0; index < regionRois.length; index++) {
            Roi roi = regionRois[index];
            int number = index + 1;
            if (roi == null) {
                throw new IllegalArgumentException(
                        "Observation-region ROI " + number + " is null.");
            }
            if (!roi.isArea()) {
                throw new IllegalArgumentException(
                        "Observation-region ROI " + number
                                + " is not an area selection.");
            }
            bounds = bounds == null
                    ? new Rectangle(roi.getBounds())
                    : bounds.union(roi.getBounds());
        }
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            throw new IllegalArgumentException(
                    "Observation-region ROIs have no positive-area bounds.");
        }
        CalibrationInfo calibration = CalibrationInfo.from(reference);
        return new RectangularWindow(
                calibration.xEdge(bounds.x),
                calibration.yEdge(bounds.y),
                calibration.xEdge(bounds.x + bounds.width),
                calibration.yEdge(bounds.y + bounds.height));
    }

    private static int fill(ImageProcessor processor, Roi roi, int label) {
        Rectangle bounds = roi.getBounds();
        ImageProcessor mask = roi.getMask();
        int painted = 0;
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                if (mask != null && mask.getPixel(x, y) == 0) continue;
                int globalX = bounds.x + x;
                int globalY = bounds.y + y;
                if (globalX >= 0 && globalX < processor.getWidth()
                        && globalY >= 0 && globalY < processor.getHeight()) {
                    int existing = processor.get(globalX, globalY);
                    if (existing != 0 && existing != label) {
                        throw new IllegalArgumentException(
                                "ROI " + label + " overlaps ROI " + existing
                                        + " at pixel (" + globalX + ", "
                                        + globalY + ").");
                    }
                    processor.set(globalX, globalY, label);
                    painted++;
                }
            }
        }
        return painted;
    }
}
