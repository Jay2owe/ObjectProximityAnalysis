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

    /**
     * Decimal places for showing {@code value} in a numeric field: at least
     * {@code minimum}, more when needed to show it exactly (up to 9), so a
     * dialog reopened by Back shows what was typed, not a rounded copy.
     */
    static int digits(double value, int minimum) {
        if (!Double.isFinite(value)) return minimum;
        for (int digits = minimum; digits < 9; digits++) {
            String shown = String.format(java.util.Locale.ROOT, "%." + digits + "f", value);
            if (Double.parseDouble(shown) == value) return digits;
        }
        return 9;
    }
}
