/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regressions found by the real-dialog GUI checks of 0.4.0 (a non-headless
 * Fiji driven by the maintainers' GUI harness). The dialog and Recorder
 * behaviour itself is re-checked there; these pin the logic underneath.
 */
public class GuiCheckRegressionTest {

    @Test
    public void rgbLabelImageIsRejectedWithAMessageNamingRgb() {
        // 0.4.0 read each packed colour as a label, so a coloured copy of a
        // label image ran silently with meaningless objects.
        ColorProcessor colour = new ColorProcessor(16, 16);
        colour.set(2, 2, 0xff0000);
        colour.set(10, 10, 0x00ff00);
        ShortProcessor labels = new ShortProcessor(16, 16);
        labels.set(4, 4, 1);
        try {
            OPA.run(Arrays.asList(
                    new ImagePlus("colour", colour),
                    new ImagePlus("labels", labels)));
            fail("an RGB label image must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("RGB"));
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("colour"));
        }
    }

    @Test
    public void defaultBatchPatternFindsUpperCaseTifFiles() throws Exception {
        // Many microscopes write .TIF; the 0.4.0 default pattern was
        // case-sensitive and silently left those samples out of the preview.
        File directory = Files.createTempDirectory("opa-upper-case").toFile();
        try {
            for (String name : new String[]{"s1_A.tif", "s1_B.tif",
                    "s2_A.TIF", "s2_B.TIF", "s3_A.Tiff", "s3_B.Tiff"}) {
                assertTrue(new File(directory, name).createNewFile());
            }
            String preview = OPABatchRunner.preview(OPABatchParameters.builder(
                            directory, OPA_Batch.DEFAULT_REGEX, 2)
                    .recursive(false)
                    .autoSave(false)
                    .build());
            assertTrue(preview, preview.startsWith("3 group(s)"));
            assertTrue(preview, preview.contains("[A] s2_A.TIF"));
            assertTrue(preview, preview.contains("[B] s3_B.Tiff"));
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) Files.delete(file.toPath());
            Files.delete(directory.toPath());
        }
    }

    @Test
    public void resultWindowTitlesAreReadableAndUnique() {
        // 0.4.0 titled windows with the file identity keys, e.g.
        // "OPA Histogram - A.tif_to_B.tif__<64 hex>__Centre-Centre__NN1".
        String hash = "67bc5070f1b251d8f596b4d9dbd47760f9b6041ceec002fe1de6230b8bea8192";
        String other = "d9b77e81b0adcb71b37c3d74e2137327421a5550bd209ab2c4537954e1479573";
        assertEquals("A.tif", WindowTitles.readable("A.tif__Centroids__" + hash));
        assertEquals("A.tif to B.tif, Centre-Centre, NN1",
                WindowTitles.readable("A.tif_to_B.tif__" + hash + "__Centre-Centre__NN1"));
        assertEquals("A.tif to B.tif", WindowTitles.readable("A.tif_to_B.tif__" + hash));
        assertEquals("A.tif, L_MINUS_R",
                WindowTitles.readable("A.tif__L_MINUS_R__" + hash));

        Map<String, ResultsTable> tables = new LinkedHashMap<String, ResultsTable>();
        tables.put("A.tif__K__" + hash, new ResultsTable());
        tables.put("A.tif__K__" + other, new ResultsTable());
        List<String> titles = new ArrayList<String>(
                WindowTitles.titled("OPA Curve - ", tables).keySet());
        assertEquals(Arrays.asList("OPA Curve - A.tif, K", "OPA Curve - A.tif, K (2)"), titles);
        for (String title : titles) {
            assertFalse(title, title.matches(".*[0-9a-f]{64}.*"));
        }
    }

    @Test
    public void aFilledRoiSetBeyondTheChannelCountIsRefused() {
        // With only the reference image open the channel count defaults to 1;
        // 0.4.0 then ignored a filled-in ROI set 2 without a word.
        Object_Proximity_Analysis.checkNoUnusedRoiSets(
                new String[]{"a.zip", "", "", "", ""}, 1);
        Object_Proximity_Analysis.checkNoUnusedRoiSets(
                new String[]{"a.zip", "b.zip", "", "", ""}, 2);
        try {
            Object_Proximity_Analysis.checkNoUnusedRoiSets(
                    new String[]{"a.zip", "b.zip", "", "", ""}, 1);
            fail("a filled ROI set 2 with one channel must be refused");
        } catch (IllegalArgumentException expected) {
            assertEquals("ROI set 2 is filled in but Channel count is 1. "
                    + "Set Channel count to 2 or clear ROI set 2.",
                    expected.getMessage());
        }
    }

    @Test
    public void reopenedNumericFieldsShowTheValueExactly() {
        // Back reopens the batch settings; a typed 2.5555 must not come back
        // rounded to the field's usual three decimals.
        assertEquals(3, DialogNumbers.digits(0.0, 3));
        assertEquals(4, DialogNumbers.digits(2.5555, 3));
        assertEquals(0, DialogNumbers.digits(119, 0));
        assertEquals(1, DialogNumbers.digits(2.5, 0));
    }
}
