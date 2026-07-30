/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.spatial;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpatialStatisticsTest {

    private static final RectangularWindow WINDOW =
            new RectangularWindow(0.0, 0.0, 10.0, 10.0);

    @Test
    public void computesExactUncorrectedKForKnownPair() {
        double[][] points = {{2.0, 5.0}, {4.0, 5.0}};
        double[] values = SpatialStatistics.computeK(
                points,
                WINDOW,
                new double[]{1.0, 2.0},
                EdgeCorrection.NONE);

        assertEquals(0.0, values[0], 1.0e-12);
        assertEquals(100.0, values[1], 1.0e-12);
    }

    @Test
    public void translationCorrectionUsesRectangularOverlap() {
        double[][] points = {{2.0, 5.0}, {4.0, 5.0}};
        double[] values = SpatialStatistics.computeK(
                points,
                WINDOW,
                new double[]{2.0},
                EdgeCorrection.TRANSLATION);

        // Two ordered pairs. Each has overlap 8*10 and weight 100/80.
        assertEquals(125.0, values[0], 1.0e-12);
    }

    @Test
    public void borderCorrectionExcludesIneligibleAnchors() {
        double[][] points = {
                {0.5, 5.0},
                {5.0, 5.0},
                {6.0, 5.0}
        };
        double[] values = SpatialStatistics.computeK(
                points,
                WINDOW,
                new double[]{1.0},
                EdgeCorrection.BORDER);

        // Two eligible anchors each see the other interior point.
        assertEquals(50.0, values[0], 1.0e-12);
    }

    @Test
    public void computesLAndLMinusRAsDistinctQuantities() {
        double[] radii = {1.0, 2.0};
        double[] k = {
                Math.PI,
                4.0 * Math.PI
        };
        assertArrayEquals(
                new double[]{1.0, 2.0},
                SpatialStatistics.computeL(k),
                1.0e-12);
        assertArrayEquals(
                new double[]{0.0, 0.0},
                SpatialStatistics.computeLMinusR(k, radii),
                1.0e-12);
    }

    @Test
    public void computesExactGAndCrossG() {
        double[][] points = {
                {1.0, 1.0},
                {4.0, 1.0},
                {9.0, 1.0}
        };
        assertArrayEquals(
                new double[]{0.0, 2.0 / 3.0, 1.0},
                SpatialStatistics.computeG(points, new double[]{2.0, 3.0, 5.0}),
                1.0e-12);

        double[][] source = {{1.0, 1.0}, {8.0, 1.0}};
        double[][] target = {{4.0, 1.0}};
        assertArrayEquals(
                new double[]{0.0, 0.5, 1.0},
                SpatialStatistics.computeCrossG(
                        source, target, new double[]{2.0, 3.0, 4.0}),
                1.0e-12);
    }

    @Test
    public void pairCorrelationIsOneForCsrExpectedK() {
        double[] radii = {1.0, 2.0, 3.0};
        double[] expectedK = SpatialStatistics.expected(
                PatternFunction.K, radii, 1.0);
        assertArrayEquals(
                new double[]{1.0, 1.0, 1.0},
                SpatialStatistics.pairCorrelationFromK(expectedK, radii),
                1.0e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pairCorrelationRejectsRadiusDependentBorderRiskSet() {
        double[][] points = {
                {1.5, 4.9},
                {1.5, 5.0},
                {1.5, 5.1},
                {5.0, 5.0}
        };
        SpatialStatistics.computePairCorrelation(
                points,
                WINDOW,
                new double[]{1.0, 2.0},
                EdgeCorrection.BORDER);
    }

    @Test
    public void monteCarloEnvelopeIsDeterministicAndRecordsResolution() {
        double[][] lattice = {
                {2.0, 2.0}, {5.0, 2.0}, {8.0, 2.0},
                {2.0, 5.0}, {5.0, 5.0}, {8.0, 5.0},
                {2.0, 8.0}, {5.0, 8.0}, {8.0, 8.0}
        };
        double[] radii = {1.0, 2.0, 3.0};
        MonteCarloResult first = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.L_MINUS_R,
                lattice,
                WINDOW,
                radii,
                EdgeCorrection.TRANSLATION,
                19,
                1234L);
        MonteCarloResult second = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.L_MINUS_R,
                lattice,
                WINDOW,
                radii,
                EdgeCorrection.TRANSLATION,
                19,
                1234L);

        assertArrayEquals(first.getLower(), second.getLower(), 0.0);
        assertArrayEquals(first.getUpper(), second.getUpper(), 0.0);
        assertEquals(first.getGlobalPValue(), second.getGlobalPValue(), 0.0);
        assertEquals(1234L, first.getSeed());
        assertEquals(19, first.getSimulations());
        assertEquals(20, first.getRankSampleCount());
        assertEquals(1.0 / 20.0, first.getMinimumAchievablePValue(), 0.0);
        assertTrue(first.getGlobalPValue() >= 1.0 / 20.0);
        assertTrue(first.getGlobalPValue() <= 1.0);
        assertEquals(
                Math.rint(first.getGlobalPValue() * 20.0),
                first.getGlobalPValue() * 20.0,
                1.0e-12);
    }

    @Test
    public void bivariateEnvelopePreservesBothPointCounts() {
        double[][] source = {{2.0, 2.0}, {8.0, 8.0}};
        double[][] target = {{2.0, 8.0}, {5.0, 5.0}, {8.0, 2.0}};
        MonteCarloResult result = MonteCarloAnalyzer.analyzeBivariate(
                PatternFunction.CROSS_K,
                source,
                target,
                WINDOW,
                new double[]{2.0, 4.0},
                EdgeCorrection.TRANSLATION,
                9,
                55L);

        assertEquals(PatternFunction.CROSS_K, result.getFunction());
        assertEquals(9, result.getSimulations());
        assertEquals(2, result.getObserved().length);
    }

    @Test
    public void clusteredPatternHasMoreSmallScalePairsThanRegularPattern() {
        double[][] clustered = {
                {4.8, 4.8}, {5.2, 4.8}, {4.8, 5.2}, {5.2, 5.2}
        };
        double[][] regular = {
                {2.0, 2.0}, {8.0, 2.0}, {2.0, 8.0}, {8.0, 8.0}
        };
        double radius = 1.0;
        double clusterK = SpatialStatistics.computeK(
                clustered,
                WINDOW,
                new double[]{radius},
                EdgeCorrection.NONE)[0];
        double regularK = SpatialStatistics.computeK(
                regular,
                WINDOW,
                new double[]{radius},
                EdgeCorrection.NONE)[0];

        assertTrue(clusterK > regularK);
        assertEquals(0.0, regularK, 0.0);
    }

    @Test
    public void computesDirectedCrossKForKnownPoints() {
        double[][] source = {{2.0, 2.0}};
        double[][] target = {{3.0, 2.0}, {9.0, 9.0}};
        double[] crossK = SpatialStatistics.computeCrossK(
                source,
                target,
                WINDOW,
                new double[]{1.0},
                EdgeCorrection.NONE);

        assertEquals(50.0, crossK[0], 1.0e-12);
    }

    @Test
    public void insufficientPointPatternsAreExplicitlyUndefined() {
        double[][] onePoint = {{5.0, 5.0}};
        double[] radii = {1.0, 2.0};
        double[] k = SpatialStatistics.computeK(
                onePoint, WINDOW, radii, EdgeCorrection.TRANSLATION);
        double[] g = SpatialStatistics.computeG(onePoint, radii);
        assertTrue(Double.isNaN(k[0]));
        assertTrue(Double.isNaN(k[1]));
        assertTrue(Double.isNaN(g[0]));
        assertTrue(Double.isNaN(g[1]));

        MonteCarloResult result = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.K,
                onePoint,
                WINDOW,
                radii,
                EdgeCorrection.TRANSLATION,
                99,
                7L);
        assertEquals(PatternStatus.INSUFFICIENT_POINTS, result.getStatus());
        assertTrue(Double.isNaN(result.getGlobalPValue()));
        assertTrue(Double.isNaN(result.getMinimumAchievablePValue()));
        assertEquals(0, result.getRankSampleCount());
    }

    @Test
    public void entirelyUndefinedBorderCurveDoesNotReportSignificance() {
        double[][] points = {{1.0, 1.0}, {9.0, 9.0}};
        MonteCarloResult result = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.K,
                points,
                WINDOW,
                new double[]{6.0, 7.0},
                EdgeCorrection.BORDER,
                99,
                11L);

        assertEquals(PatternStatus.NO_VALID_RADII, result.getStatus());
        assertTrue(Double.isNaN(result.getGlobalPValue()));
        assertTrue(Double.isNaN(result.getMaximumDeviation()));
        assertEquals(0, result.getRankSampleCount());
    }

    @Test
    public void observedOnlyMonteCarloRankIsMarkedIncomplete() {
        double[][] points = {{5.0, 5.0}, {6.0, 5.0}};
        MonteCarloResult result = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.K,
                points,
                WINDOW,
                new double[]{5.0},
                EdgeCorrection.BORDER,
                9,
                19L);

        assertEquals(PatternStatus.INCOMPLETE_MONTE_CARLO, result.getStatus());
        assertEquals(1, result.getRankSampleCount());
        assertTrue(Double.isNaN(result.getGlobalPValue()));
        assertTrue(Double.isNaN(result.getMinimumAchievablePValue()));
    }

    @Test
    public void partiallyValidMonteCarloRankIsAlsoMarkedIncomplete() {
        double[][] points = {{5.0, 5.0}, {6.0, 5.0}};
        MonteCarloResult partial = null;
        for (long seed = 1L; seed <= 1000L; seed++) {
            MonteCarloResult candidate = MonteCarloAnalyzer.analyzeUnivariate(
                    PatternFunction.K,
                    points,
                    WINDOW,
                    new double[]{4.0},
                    EdgeCorrection.BORDER,
                    9,
                    seed);
            if (candidate.getRankSampleCount() > 1
                    && candidate.getRankSampleCount() < 10) {
                partial = candidate;
                break;
            }
        }
        assertTrue("Expected a deterministic partially valid rank", partial != null);
        assertEquals(PatternStatus.INCOMPLETE_MONTE_CARLO, partial.getStatus());
        assertTrue(Double.isNaN(partial.getGlobalPValue()));
    }

    @Test
    public void incompletePointwiseEnvelopeIsCountedAndSuppressed() {
        double[][] points = {{5.0, 5.0}, {6.0, 5.0}};
        MonteCarloResult result = MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.K,
                points,
                WINDOW,
                new double[]{0.0, 4.5},
                EdgeCorrection.BORDER,
                99,
                1L);

        int[] counts = result.getEnvelopeSampleCounts();
        assertEquals(99, counts[0]);
        assertTrue(counts[1] > 0);
        assertTrue(counts[1] < 99);
        assertTrue(Double.isNaN(result.getLower()[1]));
        assertTrue(Double.isNaN(result.getUpper()[1]));
        assertTrue(!result.hasCompletePointwiseEnvelope());
        assertEquals(PatternStatus.OK, result.getStatus());
        assertEquals(100, result.getRankSampleCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void gAnalyzerRejectsPointsOutsideItsObservationWindow() {
        MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.G,
                new double[][]{{20.0, 20.0}, {21.0, 20.0}},
                WINDOW,
                new double[]{1.0},
                EdgeCorrection.NONE,
                3,
                1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void crossGAnalyzerRejectsTargetsOutsideItsObservationWindow() {
        MonteCarloAnalyzer.analyzeBivariate(
                PatternFunction.CROSS_G,
                new double[][]{{5.0, 5.0}},
                new double[][]{{20.0, 20.0}},
                WINDOW,
                new double[]{1.0},
                EdgeCorrection.NONE,
                3,
                1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void gAnalyzerRejectsNullEdgeCorrection() {
        MonteCarloAnalyzer.analyzeUnivariate(
                PatternFunction.G,
                new double[][]{{5.0, 5.0}, {6.0, 5.0}},
                WINDOW,
                new double[]{1.0},
                null,
                3,
                1L);
    }
}
