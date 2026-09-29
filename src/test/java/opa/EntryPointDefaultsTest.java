/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Defaults and fallbacks the two Fiji entry points rely on. The dialogs
 * themselves cannot be built in a headless test JVM; their wiring is checked
 * in a real Fiji.
 */
public class EntryPointDefaultsTest {

    @Test
    public void dialogsAndBuilderShareOneSimulationDefault() {
        // The dialogs used to pre-fill 99 while the API defaulted to 119, so a
        // recorded macro and a script gave different envelopes.
        assertEquals(119, OPAParameters.DEFAULT_SIMULATIONS);
        assertEquals(OPAParameters.DEFAULT_SIMULATIONS,
                OPAParameters.builder().build().getSimulations());
    }

    @Test
    public void simulationHintStatesTheRuleAndTheDefault() {
        String hint = Object_Proximity_Analysis.SIMULATION_HINT;
        assertTrue(hint, hint.contains("1/(simulations+1)"));
        assertTrue(hint, hint.contains("119 simulations give an exact 5% envelope"));
    }

    @Test
    public void blankBatchOutputFieldMeansTheInputFolder() {
        assertNull(OPA_Batch.outputDirectory(""));
        assertNull(OPA_Batch.outputDirectory("   "));
        assertNull(OPA_Batch.outputDirectory(null));
        assertEquals(new File("out"), OPA_Batch.outputDirectory(" out "));
    }

    @Test
    public void autoSavedBatchWithBlankOutputWritesIntoTheInputFolder()
            throws Exception {
        // Regression: the batch dialog rejected a blank output folder with
        // auto-save on, although the runner already falls back to the input
        // folder. The blank field now reaches the runner as null.
        File directory = Files.createTempDirectory("opa-blank-output").toFile();
        try {
            ImagePlus image = new ImagePlus("labels", new ByteProcessor(8, 8));
            image.getProcessor().set(2, 2, 1);
            image.getProcessor().set(5, 5, 2);
            IJ.saveAsTiff(image, new File(directory, "s1_A.tif").getAbsolutePath());
            image.close();

            OPABatchParameters parameters = OPABatchParameters.builder(
                            directory, "(s\\d+)_([A])\\.tif", 2)
                    .recursive(false)
                    .analysisTemplate(OPAParameters.builder()
                            .runDistances(true)
                            .runPattern(false)
                            .build())
                    .autoSave(true)
                    .outputDirectory(OPA_Batch.outputDirectory(""))
                    .build();

            OPABatchResult result = OPABatchRunner.run(parameters);
            assertEquals(1, result.getProcessedGroups());
            File saved = new File(directory, "Object Proximity Analysis");
            assertTrue("output folder in the input folder", saved.isDirectory());
            String[] contents = saved.list();
            assertTrue(contents != null && contents.length > 0);
        } finally {
            delete(directory);
        }
    }

    @Test
    public void threeDimensionalPatternRejectionNamesNoVersion() {
        ImagePlus image = new ImagePlus("stack", stack());
        ij.measure.Calibration calibration = new ij.measure.Calibration();
        calibration.setUnit("um");
        image.setCalibration(calibration);
        boolean rejected = false;
        try {
            OPA.run(OPAParameters.builder(image)
                    .runDistances(false)
                    .runPattern(true)
                    .build());
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertTrue(exception.getMessage(), exception.getMessage().startsWith(
                    "Point-pattern analysis is 2D only. "));
        }
        assertTrue(rejected);
    }

    private static ij.ImageStack stack() {
        ij.ImageStack stack = new ij.ImageStack(8, 8);
        for (int z = 0; z < 3; z++) {
            ByteProcessor slice = new ByteProcessor(8, 8);
            slice.set(2 + z, 2, z + 1);
            stack.addSlice(slice);
        }
        return stack;
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }
}
