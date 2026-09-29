/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.gui.Roi;

import java.io.IOException;

/**
 * Public, dialog-free facade for converting ROI Manager sets to label images.
 */
public final class OPALabelImages {

    private OPALabelImages() {
    }

    public static ImagePlus fromRois(ImagePlus reference, Roi[] rois) {
        return LabelUtils.roiSetToLabelImage(reference, rois);
    }

    public static ImagePlus fromRoiSet(ImagePlus reference, String path)
            throws IOException {
        Roi[] rois = LabelUtils.loadRoiSet(path);
        // An empty set would otherwise become a channel with no objects and
        // every distance from it would silently be missing.
        if (rois.length == 0) {
            throw new IllegalArgumentException(
                    "ROI set " + new java.io.File(path).getName()
                            + " contains no area ROIs.");
        }
        ImagePlus labels = LabelUtils.roiSetToLabelImage(reference, rois);
        labels.setTitle(baseName(path));
        return labels;
    }

    private static String baseName(String path) {
        String name = new java.io.File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
