/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import sc.fiji.opa.core.spatial.RectangularWindow;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    public void boundingWindowUsesImageJSpatialOrigins() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(20, 20));
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 2.0;
        calibration.xOrigin = 1.0;
        calibration.yOrigin = 2.0;
        calibration.setUnit("um");
        reference.setCalibration(calibration);

        RectangularWindow window = LabelUtils.boundingWindow(
                reference,
                new Roi[]{new Roi(2, 3, 4, 5)});

        assertEquals(0.5, window.getMinX(), 1.0e-12);
        assertEquals(2.0, window.getMinY(), 1.0e-12);
        assertEquals(2.5, window.getMaxX(), 1.0e-12);
        assertEquals(12.0, window.getMaxY(), 1.0e-12);
    }

    @Test
    public void lineCannotDefineObservationWindow() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(20, 20));
        boolean rejected = false;
        try {
            LabelUtils.boundingWindow(
                    reference,
                    new Roi[]{new Line(2, 3, 12, 13)});
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage().contains(
                    "not an area selection"));
        }
        assertTrue(rejected);
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

    @Test
    public void lineRoiIsRejectedInsteadOfBecomingItsBoundingBox() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(10, 10));
        boolean rejected = false;
        try {
            OPALabelImages.fromRois(
                    reference,
                    new Roi[]{new Line(1, 1, 4, 4)});
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage().contains(
                    "not an area selection"));
        }
        assertTrue(rejected);
    }

    @Test
    public void outOfRangeZRoiIsRejectedInsteadOfExtruded() {
        ImageStack stack = new ImageStack(10, 10);
        for (int z = 0; z < 3; z++) {
            stack.addSlice(new ByteProcessor(10, 10));
        }
        ImagePlus reference = new ImagePlus("reference", stack);
        Roi roi = new Roi(1, 1, 2, 2);
        roi.setPosition(5);

        boolean rejected = false;
        try {
            OPALabelImages.fromRois(reference, new Roi[]{roi});
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage().contains("Z position 5"));
            assertTrue(exception.getMessage().contains("1-3"));
        }
        assertTrue(rejected);
    }

    @Test
    public void emptyRoiSetIsRejectedInsteadOfBecomingAnEmptyChannel()
            throws Exception {
        // Regression: a zip with no .roi entries gave a channel with no
        // objects and no warning.
        File directory = Files.createTempDirectory("opa-empty-rois").toFile();
        try {
            File zip = new File(directory, "Empty.zip");
            ZipOutputStream output = new ZipOutputStream(
                    new FileOutputStream(zip));
            try {
                output.putNextEntry(new ZipEntry("notes.txt"));
                output.write("no rois".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            } finally {
                output.close();
            }
            boolean rejected = false;
            try {
                OPALabelImages.fromRoiSet(reference(), zip.getAbsolutePath());
            } catch (IllegalArgumentException exception) {
                rejected = true;
                assertEquals("ROI set Empty.zip contains no area ROIs.",
                        exception.getMessage());
            }
            assertTrue(rejected);
        } finally {
            delete(directory);
        }
    }

    @Test
    public void missingRoiSetGivesAPlainMessage() throws Exception {
        String path = new File(Files.createTempDirectory("opa-missing").toFile(),
                "absent.zip").getAbsolutePath();
        boolean rejected = false;
        try {
            LabelUtils.loadRoiSet(path);
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertEquals("ROI set not found: " + path, exception.getMessage());
        } finally {
            new File(path).getParentFile().delete();
        }
        assertTrue(rejected);
    }

    @Test
    public void upperCaseRoiExtensionsLoadUnderATurkishLocale()
            throws Exception {
        // Regression: toLowerCase() without a locale turns "I" into a dotless
        // i under Turkish, so ".ROI" never matched ".roi".
        Locale original = Locale.getDefault();
        File directory = Files.createTempDirectory("opa-locale").toFile();
        try {
            File single = new File(directory, "OBJECT.ROI");
            assertTrue(RoiEncoder.save(
                    new Roi(1, 1, 3, 3), single.getAbsolutePath()));
            File zip = new File(directory, "SET.ZIP");
            ZipOutputStream output = new ZipOutputStream(
                    new FileOutputStream(zip));
            try {
                output.putNextEntry(new ZipEntry("0001-CELL.ROI"));
                output.write(RoiEncoder.saveAsByteArray(new Roi(5, 5, 2, 2)));
                output.closeEntry();
            } finally {
                output.close();
            }

            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(1, LabelUtils.loadRoiSet(single.getAbsolutePath()).length);
            assertEquals(1, LabelUtils.loadRoiSet(zip.getAbsolutePath()).length);
        } finally {
            Locale.setDefault(original);
            delete(directory);
        }
    }

    private static ImagePlus reference() {
        return new ImagePlus("reference", new ByteProcessor(10, 10));
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }

    @Test
    public void objectRoiMustPaintAnInBoundsPixel() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(10, 10));
        boolean rejected = false;
        try {
            OPALabelImages.fromRois(
                    reference,
                    new Roi[]{new Roi(20, 20, 2, 2)});
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage().contains(
                    "does not cover any in-bounds image pixels"));
        }
        assertTrue(rejected);
    }
}
