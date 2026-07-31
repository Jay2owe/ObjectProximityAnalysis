/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

/**
 * Exact parsing for ImageJ numeric fields that represent whole numbers.
 */
final class DialogNumbers {

    private DialogNumbers() {
    }

    static int wholeNumber(double value, String name) {
        if (!Double.isFinite(value)
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new IllegalArgumentException(
                    name + " must be a whole number in the supported integer range.");
        }
        return (int) value;
    }
}
