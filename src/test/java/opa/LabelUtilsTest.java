/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import opa.spatial.RectangularWindow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabelUtilsTest {

    @Test
    public void convertsRoisToDistinctLabels() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(10, 10));
        Roi[] rois = {
                new Roi(1, 1, 2, 2),
                new Roi(6, 6, 2, 2)
        };
        ImagePlus labels = OPALabelImages.fromRois(reference, rois);

        assertEquals(1, labels.getProcessor().get(1, 1));
        assertEquals(2, labels.getProcessor().get(6, 6));
        assertEquals(0, labels.getProcessor().get(4, 4));
    }

    @Test
    public void computesCalibratedUnionBoundingWindow() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(20, 20));
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 2.0;
        calibration.setUnit("um");
        reference.setCalibration(calibration);
        Roi[] regions = {
                new Roi(2, 3, 4, 5),
                new Roi(10, 4, 2, 3)
        };

        RectangularWindow window =
                LabelUtils.boundingWindow(reference, regions);
        assertEquals(1.0, window.getMinX(), 1.0e-12);
        assertEquals(6.0, window.getMinY(), 1.0e-12);
        assertEquals(6.0, window.getMaxX(), 1.0e-12);
        assertEquals(16.0, window.getMaxY(), 1.0e-12);
    }

    @Test
    public void overlappingObjectRoisAreRejectedWithBothLabels() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(10, 10));
        Roi[] rois = {
                new Roi(1, 1, 4, 4),
                new Roi(3, 3, 4, 4)
        };

        boolean rejected = false;
        try {
            OPALabelImages.fromRois(reference, rois);
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage().contains("ROI 2"));
            assertTrue(exception.getMessage().contains("ROI 1"));
            assertTrue(exception.getMessage().contains("(3, 3)"));
        }
        assertTrue(rejected);
    }
}
