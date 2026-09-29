/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class UserErrorsTest {

    @Test
    public void rejectedSettingsAreMessages() {
        assertEquals(UserErrors.Kind.MESSAGE, UserErrors.classify(
                new IllegalArgumentException("Channel count must be between 1 and 5.")));
        // A malformed number typed into a field is a subclass of the above.
        assertEquals(UserErrors.Kind.MESSAGE, UserErrors.classify(
                new NumberFormatException("For input string: \"x\"")));
    }

    @Test
    public void unreadableFilesAreMessages() {
        assertEquals(UserErrors.Kind.MESSAGE,
                UserErrors.classify(new IOException("disk full")));
        assertEquals(UserErrors.Kind.MESSAGE,
                UserErrors.classify(new FileNotFoundException("rois.zip")));
    }

    @Test
    public void exhaustedMemoryIsReportedAsSuch() {
        assertEquals(UserErrors.Kind.OUT_OF_MEMORY,
                UserErrors.classify(new OutOfMemoryError("Java heap space")));
    }

    @Test
    public void defectsKeepTheirStackTrace() {
        assertEquals(UserErrors.Kind.UNEXPECTED,
                UserErrors.classify(new NullPointerException()));
        assertEquals(UserErrors.Kind.UNEXPECTED,
                UserErrors.classify(new IllegalStateException("bug")));
        assertEquals(UserErrors.Kind.UNEXPECTED,
                UserErrors.classify(new StackOverflowError()));
    }

    @Test
    public void emptyMessageFallsBackToTheExceptionName() {
        assertEquals("java.io.IOException",
                UserErrors.message(new IOException()));
        assertEquals("Choose a folder.",
                UserErrors.message(new IllegalArgumentException("Choose a folder.")));
    }
}
