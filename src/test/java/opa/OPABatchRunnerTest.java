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
import java.util.HashSet;
import java.util.Set;

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

    @Test
    public void autoSaveDoesNotOverwriteGroupsDifferingOnlyByExtension()
            throws Exception {
        File input = Files.createTempDirectory("opa-batch-overwrite-input").toFile();
        File output = Files.createTempDirectory("opa-batch-overwrite-output").toFile();
        try {
            saveTwoLabels(new File(input, "sample_A.tif"), "pixel");
            saveTwoLabels(new File(input, "sample_B.tif"), "pixel");
            saveTwoLabels(new File(input, "sample_A.tiff"), "pixel");
            saveTwoLabels(new File(input, "sample_B.tiff"), "pixel");

            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            input,
                            "(sample)_([AB])\\.(tif|tiff)",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(true)
                    .outputDirectory(output)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);
            assertEquals(2, result.getProcessedGroups());
            File objects = new File(
                    new File(output, "Object Proximity Analysis"), "Objects");
            File[] summaries = objects.listFiles((directory, name) ->
                    name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(2, summaries.length);
            assertFalse(summaries[0].getName().equals(summaries[1].getName()));
            Set<String> groupIdentities = new HashSet<String>();
            for (int row = 0; row < result.getDistanceSummary().size(); row++) {
                groupIdentities.add(result.getDistanceSummary()
                        .getStringValue("Group_Identity", row));
            }
            assertEquals(2, groupIdentities.size());
        } finally {
            deleteChildren(input);
            deleteChildren(output);
        }
    }

    @Test
    public void meanCurvesInterpolateOntoACommonSharedRadiusGrid()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-curve-grid").toFile();
        try {
            saveTwoLabels(
                    new File(directory, "sample1_A.tif"),
                    "pixel", 8, 8);
            saveTwoLabels(
                    new File(directory, "sample2_A.tif"),
                    "pixel", 20, 20);

            OPAParameters options = OPAParameters.builder()
                    .runDistances(false)
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radiusBins(4)
                    .simulations(1)
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
            assertEquals(1, result.getMeanCurveTables().size());
            ij.measure.ResultsTable table =
                    result.getMeanCurveTables().values().iterator().next();
            assertEquals(4, table.size());
            for (int row = 0; row < table.size(); row++) {
                assertEquals(2.0, table.getValue("Group_N", row), 0.0);
                assertEquals("OK",
                        table.getStringValue("Aggregation_Status", row));
            }
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void aggregateFilesRemainDistinctWhenUnitsSanitizeTheSame()
            throws Exception {
        File input = Files.createTempDirectory(
                "opa-batch-unit-filenames-input").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-unit-filenames-output").toFile();
        try {
            saveTwoLabels(new File(input, "sample1_A.tif"), "a/b");
            saveTwoLabels(new File(input, "sample2_A.tif"), "a?b");

            OPAParameters options = OPAParameters.builder()
                    .runDistances(false)
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0})
                    .simulations(1)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            input,
                            "(sample\\d+)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(true)
                    .outputDirectory(output)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);

            assertEquals(2, result.getProcessedGroups());
            assertEquals(2, result.getMeanCurveTables().size());
            File folder = new File(
                    new File(output, "Object Proximity Analysis"), "Folder");
            File[] curves = folder.listFiles((directory, name) ->
                    name.startsWith("OPA_Batch_Mean_Curve__")
                            && name.endsWith(".csv"));
            assertTrue(curves != null);
            assertEquals(2, curves.length);
            assertFalse(curves[0].getName().equals(curves[1].getName()));
        } finally {
            deleteChildren(input);
            deleteChildren(output);
        }
    }

    private static void saveLabel(File file, int x, int y) {
        ImagePlus image = new ImagePlus("labels", new ByteProcessor(8, 8));
        image.getProcessor().set(x, y, 1);
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
    }

    private static void saveTwoLabels(File file, String unit) {
        saveTwoLabels(file, unit, 8, 8);
    }

    private static void saveTwoLabels(File file,
                                      String unit,
                                      int width,
                                      int height) {
        ImagePlus image = new ImagePlus(
                "labels", new ByteProcessor(width, height));
        image.getProcessor().set(
                Math.max(1, width / 4),
                Math.max(1, height / 4),
                1);
        image.getProcessor().set(
                Math.min(width - 2, 3 * width / 4),
                Math.min(height - 2, 3 * height / 4),
                2);
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
