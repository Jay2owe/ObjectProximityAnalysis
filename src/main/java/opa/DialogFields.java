/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.Macro;
import ij.gui.GenericDialog;

import java.awt.GraphicsEnvironment;
import java.util.Locale;

/**
 * File and folder fields that also work in a headless Fiji.
 *
 * <p>Headless Fiji replaces {@code GenericDialog} with a version whose
 * {@code addFileField} and {@code addDirectoryField} do nothing, while
 * {@code getNextString} still reads them. Every later text field then reads
 * its neighbour's value and the last one fails, so a headless macro could not
 * run either command. A plain text field has the same macro key and the same
 * value, so it is used whenever there is no display.</p>
 */
final class DialogFields {

    private static final int COLUMNS = 30;

    private DialogFields() {
    }

    static void addFile(GenericDialog dialog, String label, String value) {
        if (textOnly(dialog)) dialog.addStringField(label, value, COLUMNS);
        else dialog.addFileField(label, value);
    }

    static void addDirectory(GenericDialog dialog, String label, String value) {
        if (textOnly(dialog)) dialog.addStringField(label, value, COLUMNS);
        else dialog.addDirectoryField(label, value);
    }

    /**
     * True without a display, or when the dialog is headless Fiji's
     * replacement, which can also be in use while a display exists.
     */
    static boolean textOnly(GenericDialog dialog) {
        return GraphicsEnvironment.isHeadless()
                || isHeadlessReplacement(dialog.getClass());
    }

    static boolean isHeadlessReplacement(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (HEADLESS_DIALOG.equals(c.getName())) return true;
        }
        return false;
    }

    /**
     * The next choice, refusing a macro value that is not one of the offered
     * items. The desktop dialog stops a macro with such a value; headless
     * Fiji's replacement silently falls back to the default item, so a
     * misspelt image title or edge correction ran on the wrong input.
     */
    static String nextChoice(GenericDialog dialog, String label) {
        String choice = dialog.getNextChoice();
        checkChoice(Macro.getOptions(), label, choice);
        return choice;
    }

    static void checkChoice(String macroOptions, String label, String choice) {
        if (macroOptions == null) return;
        String requested = Macro.getValue(
                macroOptions, label.toLowerCase(Locale.ROOT), null);
        // "&name" is a macro variable reference, resolved by ImageJ itself.
        if (requested == null || requested.startsWith("&")) return;
        if (!requested.equals(choice)) {
            throw new IllegalArgumentException("'" + requested
                    + "' is not a valid choice for " + label.replace('_', ' ')
                    + ".");
        }
    }

    private static final String HEADLESS_DIALOG =
            "net.imagej.patcher.HeadlessGenericDialog";
}
