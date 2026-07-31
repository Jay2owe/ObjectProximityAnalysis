/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DialogNumbersTest {

    @Test
    public void acceptsExactWholeNumbers() {
        assertEquals(999, DialogNumbers.wholeNumber(
                999.0, "Simulation count"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFractionalNumbers() {
        DialogNumbers.wholeNumber(1.5, "Channel count");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNumbersOutsideIntegerRange() {
        DialogNumbers.wholeNumber(
                (double) Integer.MAX_VALUE + 1.0, "Radius bins");
    }
}
