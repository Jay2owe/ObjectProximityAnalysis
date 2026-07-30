/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.measure.Calibration;
import opa.spatial.PatternFunction;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OPABatchRunnerTest {

    @Test
    public void previewsGroupsByReplacingTheChannelCapture() throws Exception {
        File directory = Files.createTempDirectory("opa-preview").toFile();
        try {
            assertTrue(new File(directory, "sample1_A.tif").createNewFile());
            assertTrue(new File(directory, "sample1_B.tif").createNewFile());
            assertTrue(new File(directory, "sample2_A.tif").createNewFile());
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample\\d+)_([AB])\\.tif",
                            2)
                    .recursive(false)
                    .autoSave(false)
                    .build();

            String preview = OPABatchRunner.preview(parameters);
            assertTrue(preview.contains("2 group(s)"));
            assertTrue(preview.contains("sample1_*.tif"));
            assertTrue(preview.contains("[A] sample1_A.tif"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void runsSingleAndMultiChannelGroupsAndAggregatesCurves()
            throws Exception {
        File directory = Files.createTempDirectory("opa-batch").toFile();
        try {
            saveLabel(new File(directory, "sample1_A.tif"), 2, 2);
            saveLabel(new File(directory, "sample1_B.tif"), 3, 2);
            saveLabel(new File(directory, "sample2_A.tif"), 5, 5);

            OPAParameters options = OPAParameters.builder()
                    .runDistances(true)
                    .runPattern(true)
                    .patternFunctions(EnumSet.of(
                            PatternFunction.K,
                            PatternFunction.CROSS_K))
                    .radii(new double[]{1.0, 2.0})
                    .simulations(2)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample\\d+)_([AB])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);
            assertEquals(2, result.getTotalGroups());
            assertEquals(2, result.getProcessedGroups());
            assertEquals(0, result.getErrorGroups());
            assertTrue(result.getDistanceSummary().size() > 0);
            assertFalse(result.getMeanCurveTables().isEmpty());
            assertFalse(result.getMeanEcdfTables().isEmpty());
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void keepsIncompatibleBatchUnitsInSeparateAggregates()
            throws Exception {
        File directory = Files.createTempDirectory("opa-batch-units").toFile();
        try {
            saveTwoLabels(
                    new File(directory, "sample1_A.tif"),
                    "pixel");
            saveTwoLabels(
                    new File(directory, "sample2_A.tif"),
                    "um");

            OPAParameters options = OPAParameters.builder()
                    .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0, 2.0})
                    .simulations(2)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample\\d+)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);
            assertEquals(2, result.getProcessedGroups());
            assertEquals(2, result.getMeanCurveTables().size());
            assertEquals(2, result.getMeanEcdfTables().size());
            boolean sawPixels = false;
            boolean sawMicrometres = false;
            for (ij.measure.ResultsTable table
                    : result.getMeanCurveTables().values()) {
                String unit = table.getStringValue("Radius_Unit", 0);
                sawPixels |= "pixel".equals(unit);
                sawMicrometres |= "\u00b5m".equals(unit);
                assertTrue(table.getStringValue("Value_Unit", 0)
                        .endsWith("^2"));
            }
            assertTrue(sawPixels);
            assertTrue(sawMicrometres);
        } finally {
            deleteChildren(directory);
        }
    }

    private static void saveLabel(File file, int x, int y) {
        ImagePlus image = new ImagePlus("labels", new ByteProcessor(8, 8));
        image.getProcessor().set(x, y, 1);
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
    }

    private static void saveTwoLabels(File file, String unit) {
        ImagePlus image = new ImagePlus("labels", new ByteProcessor(8, 8));
        image.getProcessor().set(2, 2, 1);
        image.getProcessor().set(5, 5, 2);
        if (!"pixel".equals(unit)) {
            Calibration calibration = new Calibration();
            calibration.pixelWidth = 1.0;
            calibration.pixelHeight = 1.0;
            calibration.pixelDepth = 1.0;
            calibration.setUnit(unit);
            image.setCalibration(calibration);
        }
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
    }

    private static void deleteChildren(File directory) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteChildren(child);
                child.delete();
            }
        }
        directory.delete();
    }
}
