/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import sc.fiji.opa.core.ProgressListener;

/**
 * Optional progress callback for headless analyses.
 *
 * <p>This is the documented public name and it keeps its single
 * {@code onProgress(double, String)} method, so existing callers and lambdas
 * are unaffected. It now extends the engine's own listener, which lets a
 * listener supplied here be handed straight to the engine instead of being
 * wrapped — the engine reports progress, and the plugin decides that this
 * becomes an ImageJ status bar.</p>
 */
public interface OPAProgressListener extends ProgressListener {
}
