/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

/**
 * Optional progress callback for headless analyses.
 */
public interface OPAProgressListener {

    /**
     * Receives a fraction from 0 to 1 and a short description of the current
     * operation.
     */
    void onProgress(double fraction, String message);
}
