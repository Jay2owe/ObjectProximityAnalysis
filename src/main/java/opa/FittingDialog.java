/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.gui.GUI;
import ij.gui.GenericDialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.ScrollPane;
import java.awt.Window;

/**
 * A {@link GenericDialog} that always fits on the screen. When the packed
 * dialog is taller or wider than the usable screen area (a laptop at 125%
 * display scaling offers about 1536 x 824 pixels, and ImageJ's GUI scale
 * enlarges dialogs further), its fields move into a scroll pane and the
 * OK/Cancel row stays fixed underneath. Without this Windows clamps the
 * window, and at GUI scale 1.5 the main dialog's OK button was off screen.
 */
class FittingDialog extends GenericDialog {

    /** Pixels scrolled per mouse-wheel notch or arrow click. */
    static final int SCROLL_STEP = 24;

    private boolean fitted;

    FittingDialog(String title) {
        super(title);
    }

    @Override
    public void pack() {
        super.pack();
        if (!fitted) {
            // Never pass `this` as a Window: headless Fiji re-parents
            // GenericDialog off Window, and bytecode that treats this
            // dialog as one fails verification there (VerifyError).
            Window reference = IJ.getInstance();
            fitted = fitTo(reference != null
                    ? GUI.getMaxWindowBounds(reference)
                    : GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getMaximumWindowBounds());
        }
    }

    /**
     * Moves every component except the last (GenericDialog adds its button
     * row last, just before packing) into a scroll pane when the packed
     * dialog does not fit {@code screen}.
     *
     * @return true when the dialog was rearranged
     */
    boolean fitTo(Rectangle screen) {
        Dimension packed = getSize();
        if (packed.width <= screen.width && packed.height <= screen.height) return false;
        if (!(getLayout() instanceof GridBagLayout)) return false;
        Component[] parts = getComponents();
        if (parts.length < 2) return false;
        GridBagLayout grid = (GridBagLayout) getLayout();
        GridBagConstraints[] constraints = new GridBagConstraints[parts.length];
        for (int i = 0; i < parts.length; i++) constraints[i] = grid.getConstraints(parts[i]);

        Panel content = new Panel(new GridBagLayout());
        for (int i = 0; i < parts.length - 1; i++) content.add(parts[i], constraints[i]);
        Component buttons = parts[parts.length - 1];
        remove(buttons);
        setLayout(new BorderLayout());
        ScrollPane scroll = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        scroll.add(content);
        scroll.getVAdjustable().setUnitIncrement(SCROLL_STEP);
        scroll.getHAdjustable().setUnitIncrement(SCROLL_STEP);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        Insets insets = getInsets();
        Dimension inner = content.getPreferredSize();
        int width = inner.width + scroll.getVScrollbarWidth() + insets.left + insets.right + 8;
        int height = inner.height + buttons.getPreferredSize().height
                + scroll.getHScrollbarHeight() + insets.top + insets.bottom + 8;
        setSize(Math.min(width, screen.width), Math.min(height, screen.height));
        validate();
        return true;
    }
}
