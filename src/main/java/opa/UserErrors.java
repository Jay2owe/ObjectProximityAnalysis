/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.Macro;

import java.io.IOException;

/**
 * The one place that decides whether a failure is shown to the user as a
 * message or as a stack trace.
 *
 * <p>A rejected setting or an unreadable file is something the user can fix,
 * so it is shown as a plain message. Running out of memory gets ImageJ's own
 * advice. Anything else is a defect and keeps its stack trace so it can be
 * reported.</p>
 */
final class UserErrors {

    enum Kind {
        /** The user can act on the message; no stack trace. */
        MESSAGE,
        /** The Java heap is exhausted. */
        OUT_OF_MEMORY,
        /** A defect; show the stack trace. */
        UNEXPECTED
    }

    private UserErrors() {
    }

    static Kind classify(Throwable failure) {
        if (failure instanceof IllegalArgumentException
                || failure instanceof IOException) {
            return Kind.MESSAGE;
        }
        if (failure instanceof OutOfMemoryError) return Kind.OUT_OF_MEMORY;
        return Kind.UNEXPECTED;
    }

    /** Text shown for a {@link Kind#MESSAGE} failure. */
    static String message(Throwable failure) {
        String text = failure.getMessage();
        if (text == null || text.trim().isEmpty()) return failure.toString();
        return text;
    }

    static void report(String title, Throwable failure) {
        switch (classify(failure)) {
            case MESSAGE:
                IJ.error(title, message(failure));
                // A macro that continued past a rejected setting would run its
                // later lines on results that were never produced.
                if (IJ.isMacro() || Macro.getOptions() != null) Macro.abort();
                break;
            case OUT_OF_MEMORY:
                IJ.outOfMemory(title);
                break;
            default:
                IJ.handleException(failure);
                break;
        }
    }
}
