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

/**
 * Publication-oriented plot creation, separated from the headless engine.
 */
public final class OPAPlots {

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
            String channels = pattern.getSourceChannel();
            if (pattern.isBivariate()) {
                channels += " to " + pattern.getTargetChannel();
            }

            Plot plot = new Plot(
                    "OPA L(r)-r - " + channels,
                    "Radius (" + pattern.getUnit() + ")",
                    "L(r) - r (" + pattern.getUnit() + ")");
            addEnvelope(plot, radii, lower, upper);
            plot.setColor(Color.DARK_GRAY);
            plot.setLineWidth(1);
            plot.add("line", radii, new double[radii.length]);
            plot.setColor(new Color(0, 92, 175));
            plot.setLineWidth(2);
            plot.add("line", radii, observed);
            plot.addLegend("95% Monte Carlo envelope\nCSR expectation\nObserved");
            plots.add(plot);
        }
        return plots;
    }

    private static void addEnvelope(Plot plot,
                                    double[] x,
                                    double[] lower,
                                    double[] upper) {
        double[] polygonX = new double[x.length * 2];
        double[] polygonY = new double[x.length * 2];
        for (int i = 0; i < x.length; i++) {
            polygonX[i] = x[i];
            polygonY[i] = lower[i];
            int reverse = x.length * 2 - 1 - i;
            polygonX[reverse] = x[i];
            polygonY[reverse] = upper[i];
        }
        plot.setColor(new Color(215, 215, 215));
        plot.add("filled", polygonX, polygonY);
    }
}
