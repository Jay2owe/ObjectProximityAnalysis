/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
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

    private static void saveLabel(File file, int x, int y) {
        ImagePlus image = new ImagePlus("labels", new ByteProcessor(8, 8));
        image.getProcessor().set(x, y, 1);
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
