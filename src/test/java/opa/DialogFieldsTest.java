/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DialogFieldsTest {

    @Test
    public void macroChoiceThatWasHonouredPasses() {
        DialogFields.checkChoice("edge_correction=BORDER hide_display",
                "Edge_correction", "BORDER");
        DialogFields.checkChoice("label_image_1=[cells 1.tif] run_distances",
                "Label_image_1", "cells 1.tif");
    }

    @Test
    public void choiceNotNamedByTheMacroIsNotChecked() {
        DialogFields.checkChoice("run_distances", "Edge_correction", "TRANSLATION");
        DialogFields.checkChoice(null, "Edge_correction", "TRANSLATION");
        DialogFields.checkChoice("edge_correction=&choice", "Edge_correction",
                "TRANSLATION");
    }

    @Test
    public void macroChoiceReplacedByTheDefaultIsRefused() {
        // Regression: headless Fiji silently used the default item when a
        // macro named one that does not exist.
        boolean rejected = false;
        try {
            DialogFields.checkChoice("edge_correction=SIDEWAYS",
                    "Edge_correction", "TRANSLATION");
        } catch (IllegalArgumentException exception) {
            rejected = true;
            assertEquals("'SIDEWAYS' is not a valid choice for Edge correction.",
                    exception.getMessage());
        }
        assertTrue(rejected);
    }

    @Test
    public void headlessReplacementIsRecognisedByName() {
        assertFalse(DialogFields.isHeadlessReplacement(Object.class));
        assertFalse(DialogFields.isHeadlessReplacement(ij.gui.GenericDialog.class));
        assertTrue(DialogFields.isHeadlessReplacement(
                net.imagej.patcher.HeadlessGenericDialog.class));
    }
}
