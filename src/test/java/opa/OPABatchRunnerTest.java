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
import sc.fiji.opa.core.spatial.PatternFunction;
import org.junit.Test;
import sc.fiji.opa.core.DistanceMode;

import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
    public void batchComposesCallerProgressWithOverallGroupProgress()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-progress").toFile();
        try {
            saveDiagonalLabels(new File(directory, "sample_A.tif"));
            final List<Double> fractions = new ArrayList<Double>();
            final List<String> messages = new ArrayList<String>();
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            fractions.add(fraction);
                            messages.add(message);
                        }
                    })
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
            assertFalse(fractions.isEmpty());
            assertEquals(
                    1.0,
                    fractions.get(fractions.size() - 1),
                    1.0e-12);
            assertTrue(messages.toString().contains(
                    "Batch group 1 of 1"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void trailingInvalidGroupStillCompletesCallerProgress()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-progress-trailing-invalid").toFile();
        try {
            saveDiagonalLabels(new File(directory, "a_A.tif"));
            for (char channel = 'A'; channel <= 'F'; channel++) {
                assertTrue(new File(
                        directory, "b_" + channel + ".tif").createNewFile());
            }
            final List<Double> fractions = new ArrayList<Double>();
            final List<String> messages = new ArrayList<String>();
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            fractions.add(fraction);
                            messages.add(message);
                        }
                    })
                    .build();
            OPABatchResult result = OPABatchRunner.run(
                    OPABatchParameters.builder(
                                    directory,
                                    "([ab])_([A-F])\\.tif",
                                    2)
                            .recursive(false)
                            .analysisTemplate(options)
                            .autoSave(false)
                            .build());

            assertEquals(1, result.getProcessedGroups());
            assertEquals(1, result.getSkippedGroups());
            assertEquals(
                    1.0,
                    fractions.get(fractions.size() - 1),
                    1.0e-12);
            assertTrue(messages.toString().contains(
                    "skipped invalid group"));
            assertTrue(messages.get(messages.size() - 1).startsWith(
                    "Batch complete"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void allInvalidGroupsStillNotifyCallerOfCompletion()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-progress-all-invalid").toFile();
        try {
            for (char channel = 'A'; channel <= 'F'; channel++) {
                assertTrue(new File(
                        directory, "sample_" + channel + ".tif")
                        .createNewFile());
            }
            final List<Double> fractions = new ArrayList<Double>();
            final List<String> messages = new ArrayList<String>();
            OPAParameters options = OPAParameters.builder()
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            fractions.add(fraction);
                            messages.add(message);
                        }
                    })
                    .build();
            OPABatchResult result = OPABatchRunner.run(
                    OPABatchParameters.builder(
                                    directory,
                                    "(sample)_([A-F])\\.tif",
                                    2)
                            .recursive(false)
                            .analysisTemplate(options)
                            .autoSave(false)
                            .build());

            assertEquals(0, result.getProcessedGroups());
            assertEquals(1, result.getSkippedGroups());
            assertFalse(fractions.isEmpty());
            assertEquals(
                    1.0,
                    fractions.get(fractions.size() - 1),
                    1.0e-12);
            assertTrue(messages.get(messages.size() - 1).startsWith(
                    "Batch complete"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void escapeFromInvalidGroupProgressCancelsBatch()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-cancel-invalid-progress").toFile();
        try {
            for (char channel = 'A'; channel <= 'F'; channel++) {
                assertTrue(new File(
                        directory, "sample_" + channel + ".tif")
                        .createNewFile());
            }
            OPAParameters options = OPAParameters.builder()
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            if (message.contains("skipped invalid group")) {
                                IJ.setKeyDown(KeyEvent.VK_ESCAPE);
                            }
                        }
                    })
                    .build();

            OPABatchResult result = OPABatchRunner.run(
                    OPABatchParameters.builder(
                                    directory,
                                    "(sample)_([A-F])\\.tif",
                                    2)
                            .recursive(false)
                            .analysisTemplate(options)
                            .autoSave(false)
                            .build());

            assertTrue(result.isCancelled());
            assertEquals(1, result.getSkippedGroups());
            assertEquals("SKIPPED_INVALID", result.getGroupManifest()
                    .getStringValue("Outcome", 0));
            assertFalse(IJ.escapePressed());
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
            deleteChildren(directory);
        }
    }

    @Test
    public void escapeFromInvalidGroupMarksEveryUnvisitedGroupCancelled()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-cancel-mixed-progress").toFile();
        try {
            for (char channel = 'A'; channel <= 'F'; channel++) {
                assertTrue(new File(
                        directory, "a_" + channel + ".tif")
                        .createNewFile());
                assertTrue(new File(
                        directory, "c_" + channel + ".tif")
                        .createNewFile());
            }
            saveDiagonalLabels(new File(directory, "b_A.tif"));
            OPAParameters options = OPAParameters.builder()
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            if (message.contains("skipped invalid group")) {
                                IJ.setKeyDown(KeyEvent.VK_ESCAPE);
                            }
                        }
                    })
                    .build();

            OPABatchResult result = OPABatchRunner.run(
                    OPABatchParameters.builder(
                                    directory,
                                    "([abc])_([A-F])\\.tif",
                                    2)
                            .recursive(false)
                            .analysisTemplate(options)
                            .autoSave(false)
                            .build());

            assertTrue(result.isCancelled());
            assertEquals(3, result.getTotalGroups());
            assertEquals(1, result.getValidGroups());
            assertEquals(3, result.getSkippedGroups());
            assertEquals("SKIPPED_INVALID", result.getGroupManifest()
                    .getStringValue("Outcome", 0));
            assertEquals("CANCELLED", result.getGroupManifest()
                    .getStringValue("Outcome", 1));
            assertEquals("CANCELLED", result.getGroupManifest()
                    .getStringValue("Outcome", 2));
            assertFalse(IJ.escapePressed());
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
            deleteChildren(directory);
        }
    }

    @Test
    public void escapeFromTerminalBatchProgressSavesCancelledStatus()
            throws Exception {
        File input = Files.createTempDirectory(
                "opa-batch-cancel-terminal-input").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-cancel-terminal-output").toFile();
        try {
            saveDiagonalLabels(new File(input, "sample_A.tif"));
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(
                                double fraction, String message) {
                            if (message.startsWith("Batch complete")) {
                                IJ.setKeyDown(KeyEvent.VK_ESCAPE);
                            }
                        }
                    })
                    .build();

            OPABatchResult result = OPABatchRunner.run(
                    OPABatchParameters.builder(
                                    input,
                                    "(sample)_([A])\\.tif",
                                    2)
                            .recursive(false)
                            .analysisTemplate(options)
                            .autoSave(true)
                            .outputDirectory(output)
                            .build());

            assertTrue(result.isCancelled());
            assertEquals(1, result.getProcessedGroups());
            File batchRoot = new File(
                    output, "Object Proximity Analysis/Folder");
            File[] runFolders = batchRoot.listFiles(
                    (directory, name) ->
                            name.startsWith("Batch__"));
            assertNotNull(runFolders);
            assertEquals(1, runFolders.length);
            String readme = new String(
                    Files.readAllBytes(
                            new File(runFolders[0], "README.txt").toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(readme.contains("Status: CANCELLED"));
            assertFalse(IJ.escapePressed());
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
            deleteChildren(input);
            deleteChildren(output);
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
            File folderRoot = new File(
                    new File(output, "Object Proximity Analysis"), "Folder");
            File folder = onlyBatchRunDirectory(folderRoot);
            assertTrue(folder != null);
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
            File folderRoot = new File(
                    new File(output, "Object Proximity Analysis"), "Folder");
            File batchRun = onlyBatchRunDirectory(folderRoot);
            assertTrue(batchRun != null);
            File readme = new File(batchRun, "README.txt");
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

    @Test
    public void optionalUnmatchedChannelCaptureIsExcludedFromDiscovery()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-optional-capture").toFile();
        try {
            assertTrue(new File(directory, "sample_.tif").createNewFile());
            assertTrue(new File(directory, "sample_A.tif").createNewFile());
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(sample)_([AB])?\\.tif",
                            2)
                    .recursive(false)
                    .autoSave(false)
                    .build();

            String preview = OPABatchRunner.preview(parameters);

            assertTrue(preview.contains("1 group(s)"));
            assertTrue(preview.contains("1 file(s)"));
            assertTrue(preview.contains("[A] sample_A.tif"));
            assertFalse(preview.contains("sample_.tif"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void recursiveDiscoveryExcludesThePluginOutputTree()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-output-exclusion").toFile();
        try {
            assertTrue(new File(directory, "sample_A.tif").createNewFile());
            File generated = new File(
                    directory,
                    "Object Proximity Analysis/Folder/old/generated_A.tif");
            assertTrue(generated.getParentFile().mkdirs());
            assertTrue(generated.createNewFile());
            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory,
                            "(.+)_([A])\\.tif",
                            2)
                    .recursive(true)
                    .autoSave(true)
                    .outputDirectory(directory)
                    .build();

            String preview = OPABatchRunner.preview(parameters);

            assertTrue(preview.contains("1 group(s)"));
            assertTrue(preview.contains("sample_A.tif"));
            assertFalse(preview.contains("generated_A.tif"));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void batchPreservesPrecisionAndSingletonSpreadIsUndefined()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-precision").toFile();
        try {
            saveDiagonalLabels(new File(directory, "sample_A.tif"));
            OPAParameters options = OPAParameters.builder()
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0})
                    .simulations(1)
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

            assertEquals(Math.sqrt(2.0), result.getDistanceSummary()
                    .getValue("Mean", 0), 0.0);
            ij.measure.ResultsTable curve =
                    result.getMeanCurveTables().values()
                            .iterator().next();
            assertEquals(1.0, curve.getValue("Group_N", 0), 0.0);
            assertTrue(Double.isNaN(
                    curve.getValue("SD_Observed", 0)));
            ij.measure.ResultsTable ecdf =
                    result.getMeanEcdfTables().values()
                            .iterator().next();
            assertEquals(1.0, ecdf.getValue("Group_N", 0), 0.0);
            assertTrue(Double.isNaN(
                    ecdf.getValue("SD_ECDF", 0)));
        } finally {
            deleteChildren(directory);
        }
    }

    @Test
    public void differentInputRootsGetDistinctBatchRunFolders()
            throws Exception {
        File firstInput = Files.createTempDirectory(
                "opa-batch-root-one").toFile();
        File secondInput = Files.createTempDirectory(
                "opa-batch-root-two").toFile();
        File output = Files.createTempDirectory(
                "opa-batch-root-output").toFile();
        try {
            saveDiagonalLabels(new File(firstInput, "sample_A.tif"));
            saveDiagonalLabels(new File(secondInput, "sample_A.tif"));
            OPAParameters options = OPAParameters.builder()
                    .runPattern(false)
                    .distanceModes(EnumSet.of(
                            DistanceMode.CENTRE_TO_CENTRE))
                    .build();
            for (File input : new File[]{firstInput, secondInput}) {
                OPABatchRunner.run(OPABatchParameters.builder(
                                input,
                                "(sample)_([A])\\.tif",
                                2)
                        .recursive(false)
                        .analysisTemplate(options)
                        .autoSave(true)
                        .outputDirectory(output)
                        .build());
            }

            File folderRoot = new File(
                    new File(output, "Object Proximity Analysis"), "Folder");
            File[] runs = folderRoot.listFiles((directory, name) ->
                    new File(directory, name).isDirectory()
                            && name.startsWith("Batch__"));
            assertTrue(runs != null);
            assertEquals(2, runs.length);
            assertTrue(!runs[0].getName().equals(runs[1].getName()));
        } finally {
            deleteChildren(firstInput);
            deleteChildren(secondInput);
            deleteChildren(output);
        }
    }

    /**
     * The interpolation grid's last point used to be computed as
     * a + (b - a) * n / n, which can land one unit in the last place above b.
     * That put it past every curve's last radius, so the whole final row became
     * NaN while still claiming every group had contributed.
     */
    @Test
    public void meanCurveEndpointStaysOnEveryContributingCurve()
            throws Exception {
        File directory = Files.createTempDirectory(
                "opa-batch-curve-endpoint").toFile();
        try {
            // 28 px at 0.1 um over 10 bins puts the last interpolated grid
            // point at 0.7000000000000002 while every curve stops at
            // 0.7000000000000001.
            saveCalibratedTwoLabels(
                    new File(directory, "sample1_A.tif"), 28, 28, 0.1);
            saveCalibratedTwoLabels(
                    new File(directory, "sample2_A.tif"), 28, 28, 0.1);

            OPAParameters options = OPAParameters.builder()
                    .runDistances(false)
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radiusBins(10)
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
            ij.measure.ResultsTable table =
                    result.getMeanCurveTables().values().iterator().next();
            assertTrue(table.size() > 0);
            for (int row = 0; row < table.size(); row++) {
                boolean contributed =
                        Double.isFinite(table.getValue("Mean_Observed", row));
                assertEquals(
                        "row " + row
                                + ": Group_N must count only contributing curves",
                        contributed,
                        table.getValue("Group_N", row) > 0.0);
            }
            int lastRow = table.size() - 1;
            assertEquals("the final radius must stay on every curve",
                    2.0, table.getValue("Group_N", lastRow), 0.0);
            assertTrue(Double.isFinite(
                    table.getValue("Mean_Observed", lastRow)));
            assertEquals("OK",
                    table.getStringValue("Aggregation_Status", lastRow));
        } finally {
            deleteChildren(directory);
        }
    }

    private static void saveCalibratedTwoLabels(File file,
                                                int width,
                                                int height,
                                                double pixelSize) {
        ImagePlus image = new ImagePlus(
                "labels", new ByteProcessor(width, height));
        image.getProcessor().set(width / 4, height / 4, 1);
        image.getProcessor().set(3 * width / 4, 3 * height / 4, 2);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = pixelSize;
        calibration.pixelHeight = pixelSize;
        calibration.pixelDepth = pixelSize;
        calibration.setUnit("um");
        image.setCalibration(calibration);
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
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

    private static void saveDiagonalLabels(File file) {
        ImagePlus image = new ImagePlus(
                "diagonal-labels", new ByteProcessor(8, 8));
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(2, 2, 2);
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
    }

    private static File onlyBatchRunDirectory(File folderRoot) {
        File[] runs = folderRoot.listFiles((directory, name) ->
                new File(directory, name).isDirectory()
                        && name.startsWith("Batch__"));
        return runs != null && runs.length == 1 ? runs[0] : null;
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
