/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.gui.Plot;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Publication-oriented plot creation, separated from the headless engine.
 */
public final class OPAPlots {

    private static final Color ENVELOPE_FILL = new Color(215, 215, 215);
    private static final Color ENVELOPE_LINE = new Color(128, 128, 128);

    private OPAPlots() {
    }

    public static List<Plot> lMinusRPlots(OPAResult result) {
        List<Plot> plots = new ArrayList<Plot>();
        for (PatternResult pattern : result.getPatternResults()) {
            MonteCarloResult statistics = pattern.getStatistics();
            if (statistics.getFunction() != PatternFunction.L_MINUS_R) continue;
            double[] radii = statistics.getRadii();
            double[] observed = statistics.getObserved();
            double[] lower = statistics.getLower();
            double[] upper = statistics.getUpper();
            // A curve with no defined value (too few objects) has nothing to
            // show; an empty plot window would only suggest a failure.
            if (!anyFinite(observed)) continue;
            String channels = pattern.getSourceChannel();
            if (pattern.isBivariate()) {
                channels += " to " + pattern.getTargetChannel();
            }

            Plot plot = new Plot(
                    "OPA L(r)-r - " + channels,
                    "Radius (" + pattern.getUnit() + ")",
                    "L(r) - r (" + pattern.getUnit() + ")");
            int envelopeParts = addEnvelope(plot, radii, lower, upper);
            plot.setColor(Color.DARK_GRAY);
            plot.setLineWidth(1);
            plot.add("line", radii, new double[radii.length]);
            plot.setColor(new Color(0, 92, 175));
            plot.setLineWidth(2);
            plot.add("line", radii, observed);
            // The envelope confidence depends on the simulation count, so the
            // legend reports what was delivered rather than a fixed 95%.
            // Legend labels are assigned to data sets in order; an empty label
            // leaves a set unlabelled, so a split envelope is named once.
            StringBuilder legend = new StringBuilder();
            if (envelopeParts > 0) {
                legend.append(String.format(
                        Locale.ROOT,
                        "%.1f%% Monte Carlo envelope",
                        statistics.getEnvelopeConfidencePercent()));
                for (int part = 1; part < envelopeParts; part++) {
                    legend.append('\n');
                }
                legend.append('\n');
            }
            legend.append("CSR expectation\nObserved");
            plot.addLegend(legend.toString());
            // ImageJ takes the axis range from the first data set, the
            // envelope, so an observed curve outside it (a significant
            // result) ran off the frame. Fit the range to every data set.
            plot.setLimitsToFit(false);
            plots.add(plot);
        }
        return plots;
    }

    /**
     * Fills the envelope only where both bounds are defined, one polygon per
     * unbroken run of radii. A bound is undefined where too few simulations
     * contributed, and a polygon through those points is not drawable.
     *
     * @return the number of polygons added
     */
    static int addEnvelope(Plot plot,
                           double[] x,
                           double[] lower,
                           double[] upper) {
        int parts = 0;
        int start = -1;
        for (int i = 0; i <= x.length; i++) {
            boolean defined = i < x.length
                    && Double.isFinite(x[i])
                    && Double.isFinite(lower[i])
                    && Double.isFinite(upper[i]);
            if (defined) {
                if (start < 0) start = i;
                continue;
            }
            if (start >= 0 && i - start >= 2) {
                addPolygon(plot, x, lower, upper, start, i);
                parts++;
            }
            start = -1;
        }
        return parts;
    }

    private static void addPolygon(Plot plot,
                                   double[] x,
                                   double[] lower,
                                   double[] upper,
                                   int from,
                                   int to) {
        int length = to - from;
        double[] polygonX = new double[length * 2];
        double[] polygonY = new double[length * 2];
        for (int i = 0; i < length; i++) {
            polygonX[i] = x[from + i];
            polygonY[i] = lower[from + i];
            int reverse = length * 2 - 1 - i;
            polygonX[reverse] = x[from + i];
            polygonY[reverse] = upper[from + i];
        }
        // Outline and legend text in a mid grey, fill in a light one: the
        // legend draws a label in its data set's line colour, and the light
        // fill colour alone made "Monte Carlo envelope" nearly invisible.
        plot.setColor(ENVELOPE_LINE, ENVELOPE_FILL);
        plot.add("filled", polygonX, polygonY);
    }

    private static boolean anyFinite(double[] values) {
        for (double value : values) {
            if (Double.isFinite(value)) return true;
        }
        return false;
    }
}
