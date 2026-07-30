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

import java.awt.event.KeyEvent;
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

    @Test
    public void aggregatesKeepRawChannelNamesThatSanitizeTheSame()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-channel-identities").toFile();
        try {
            saveTwoLabels(new File(directory, "sample1_A B.tif"), "pixel");
            saveTwoLabels(new File(directory, "sample2_A_B.tif"), "pixel");

            OPAParameters options = OPAParameters.builder()
                    .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0, 2.0})
                    .simulations(1)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample\\d+)_([^.]*)\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);

            assertEquals(2, result.getProcessedGroups());
            assertEquals(2, result.getMeanCurveTables().size());
            assertEquals(2, result.getMeanEcdfTables().size());
            Set<String> curveSources = new HashSet<String>();
            for (ij.measure.ResultsTable table
                    : result.getMeanCurveTables().values()) {
                curveSources.add(
                        table.getStringValue("Source_Channel", 0));
                assertEquals(1.0, table.getValue("Group_N", 0), 0.0);
            }
            assertTrue(curveSources.contains("A B"));
            assertTrue(curveSources.contains("A_B"));
            Set<String> ecdfSources = new HashSet<String>();
            for (ij.measure.ResultsTable table
                    : result.getMeanEcdfTables().values()) {
                ecdfSources.add(
                        table.getStringValue("Source_Channel", 0));
            }
            assertEquals(curveSources, ecdfSources);
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void previewSkipsImpossibleOneChannelDistanceGroups()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-impossible-preview").toFile();
        try {
            saveTwoLabels(new File(directory, "sample_A.tif"), "pixel");
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .includeSelfDistances(false)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            String preview = OPABatchRunner.preview(parameters);
            OPABatchResult result = OPABatchRunner.run(parameters);

            assertTrue(preview.contains("0 runnable"));
            assertTrue(preview.contains(
                    "one-channel distance analysis requires self-distances"));
            assertEquals(0, result.getValidGroups());
            assertEquals(0, result.getProcessedGroups());
            assertEquals(1, result.getSkippedGroups());
            assertEquals(0, result.getErrorGroups());
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void failedGroupSaveDoesNotContaminateAggregatesOrErrorCount()
            throws Exception {
        File input = Files.createTempDirectory(
                "opa-batch-failed-save-input").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-failed-save-output").toFile();
        try {
            saveTwoLabels(new File(input, "sample_A.tif"), "pixel");
            assertTrue(new File(
                    output, "Object Proximity Analysis").createNewFile());
            OPAParameters options = OPAParameters.builder()
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0})
                    .simulations(1)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            input,
                            "(sample)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(true)
                    .outputDirectory(output)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);

            assertEquals(1, result.getTotalGroups());
            assertEquals(1, result.getValidGroups());
            assertEquals(0, result.getProcessedGroups());
            assertEquals(0, result.getSkippedGroups());
            assertEquals(1, result.getErrorGroups());
            assertEquals(result.getTotalGroups(),
                    result.getProcessedGroups()
                            + result.getSkippedGroups()
                            + result.getErrorGroups());
            assertEquals(0, result.getDistanceSummary().size());
            assertEquals(0, result.getPatternSummary().size());
            assertTrue(result.getMeanCurveTables().isEmpty());
            assertTrue(result.getMeanEcdfTables().isEmpty());
            assertTrue(result.hasErrors());
            assertEquals(2, result.getErrors().size());
        } finally {
            deleteChildren(input);
            deleteChildren(output);
        }
    }

    @Test
    public void channelCapturesAreTrimmedBeforeDuplicateValidation()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-trimmed-channels").toFile();
        try {
            saveTwoLabels(new File(directory, "sample_A.tif"), "pixel");
            saveTwoLabels(new File(directory, "sample_A .tif"), "pixel");
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample)_([A ]+)\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            String preview = OPABatchRunner.preview(parameters);
            OPABatchResult result = OPABatchRunner.run(parameters);

            assertTrue(preview.contains("0 runnable"));
            assertTrue(preview.contains("duplicate channel names"));
            assertTrue(preview.contains("captured as [A ]"));
            assertEquals(0, result.getValidGroups());
            assertEquals(1, result.getSkippedGroups());
            assertTrue(result.getGroupManifest()
                    .getStringValue("Channel_Captures", 0)
                    .contains("A <- [A ]"));
            assertEquals("SKIPPED_INVALID", result.getGroupManifest()
                    .getStringValue("Outcome", 0));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void longSimilarGroupNamesAutoSaveToDistinctBoundedFiles()
            throws Exception {
        File input = Files.createTempDirectory(
                "opa-batch-long-input").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-long-output").toFile();
        try {
            String common = repeat("longname", 11);
            saveTwoLabels(new File(
                    input, common + "X_A.tif"), "pixel");
            saveTwoLabels(new File(
                    input, common + "Y_A.tif"), "pixel");
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            input,
                            "(.+)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(true)
                    .outputDirectory(output)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);

            assertEquals(2, result.getProcessedGroups());
            assertEquals(0, result.getErrorGroups());
            File objects = new File(
                    new File(output, "Object Proximity Analysis"), "Objects");
            File[] summaries = objects.listFiles((directory, name) ->
                    name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(2, summaries.length);
            assertFalse(summaries[0].getName().equals(summaries[1].getName()));
            for (File summary : summaries) {
                assertTrue(summary.getName().length() <= 184);
            }
        } finally {
            deleteChildren(input);
            deleteChildren(output);
        }
    }

    @Test
    public void previewRejectsEveryStaticallyInvalidAnalysisTemplate()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-invalid-templates").toFile();
        try {
            saveTwoLabels(new File(directory, "sample_A.tif"), "pixel");
            OPAParameters[] invalid = new OPAParameters[]{
                    OPAParameters.builder()
                            .runDistances(false)
                            .runPattern(false)
                            .build(),
                    OPAParameters.builder()
                            .runPattern(false)
                            .distanceModes(EnumSet.noneOf(DistanceMode.class))
                            .build(),
                    OPAParameters.builder()
                            .runDistances(false)
                            .patternFunctions(
                                    EnumSet.noneOf(PatternFunction.class))
                            .build(),
                    OPAParameters.builder()
                            .runPattern(false)
                            .neighborCount(0)
                            .build(),
                    OPAParameters.builder()
                            .runDistances(false)
                            .simulations(0)
                            .build()
            };
            for (OPAParameters options : invalid) {
                OPABatchParameters parameters = OPABatchParameters.builder(
                                directory,
                                "(sample)_([A])\\.tif",
                                2)
                        .recursive(false)
                        .analysisTemplate(options)
                        .autoSave(false)
                        .build();
                assertTrue(OPABatchRunner.preview(parameters)
                        .contains("0 runnable"));
                assertEquals(0,
                        OPABatchRunner.run(parameters).getValidGroups());
            }
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void cancelledBatchIsExplicitAndMarksSavedPartialOutputs()
            throws Exception {
        File input = Files.createTempDirectory(
                "opa-batch-cancel-input").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-cancel-output").toFile();
        try {
            saveTwoLabels(new File(input, "sample1_A.tif"), "pixel");
            saveTwoLabels(new File(input, "sample2_A.tif"), "pixel");
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
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

            IJ.setKeyDown(KeyEvent.VK_ESCAPE);
            OPABatchResult result = OPABatchRunner.run(parameters);
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);

            assertTrue(result.isCancelled());
            assertEquals(0, result.getProcessedGroups());
            assertEquals(2, result.getSkippedGroups());
            assertEquals(0, result.getErrorGroups());
            assertFalse(result.hasErrors());
            assertEquals("CANCELLED", result.getGroupManifest()
                    .getStringValue("Outcome", 0));
            assertEquals("CANCELLED", result.getGroupManifest()
                    .getStringValue("Outcome", 1));
            File readme = new File(
                    new File(
                            new File(output, "Object Proximity Analysis"),
                            "Folder"),
                    "README.txt");
            String text = new String(
                    Files.readAllBytes(readme.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("Status: CANCELLED"));
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
            deleteChildren(input);
            deleteChildren(output);
        }
    }

    @Test
    public void emptyUncalibratedGroupCarriesStatusesIntoManifest()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-empty-warning").toFile();
        try {
            saveEmptyLabel(new File(directory, "sample_A.tif"));
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample)_([A])\\.tif",
                            2)
                    .recursive(false)
                    .analysisTemplate(options)
                    .autoSave(false)
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);

            assertEquals(1, result.getProcessedGroups());
            assertEquals(1, result.getDistanceSummary().size());
            assertEquals("NO_SOURCE_OBJECTS", result.getDistanceSummary()
                    .getStringValue("Status", 0));
            assertTrue(result.getGroupManifest()
                    .getStringValue("Calibration_Warning", 0)
                    .contains("UNCALIBRATED_INPUT"));
            assertTrue(result.getGroupManifest()
                    .getStringValue("Analysis_Warnings", 0)
                    .contains("DISTANCE:NO_SOURCE_OBJECTS"));
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

    private static void saveEmptyLabel(File file) {
        ImagePlus image = new ImagePlus(
                "empty-labels", new ByteProcessor(8, 8));
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) text.append(value);
        return text.toString();
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
