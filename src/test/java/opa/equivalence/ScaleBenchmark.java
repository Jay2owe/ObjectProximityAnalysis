/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.IJ;
import ij.ImagePlus;
import opa.OPA;
import opa.OPAParameters;
import opa.OPAResult;
import org.junit.Assume;
import org.junit.Test;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.PatternFunction;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

/**
 * Opt-in scale benchmark: times the real analysis on seeded synthetic scenes
 * and fingerprints its complete output, so speed work can be measured and
 * shown to leave every result bit-identical.
 *
 * <p>Skipped unless {@code -Dopa.benchmark=true}. Other properties:
 * {@code opa.benchmark.label} (output name, default {@code run}),
 * {@code opa.benchmark.budgetMillis} (a family stops growing once a median
 * exceeds it, default ten minutes), {@code opa.benchmark.families}
 * (comma-separated subset of {@code D2,D3,P}), {@code opa.benchmark.repeats}
 * (timed runs per parallelism, default 3), {@code opa.benchmark.minSize} and
 * {@code opa.benchmark.maxSize} (skip sizes outside the range, for profiling
 * one case) and {@code opa.benchmark.parallelism} ({@code both}, the default,
 * or {@code default} or {@code serial} to time only one path; the dumps do
 * not depend on it). {@code opa.benchmark.timeoutMinutes} cancels a run
 * that takes longer and records the case as unfinished, and
 * {@code opa.benchmark.ignoreBudget} keeps measuring larger sizes after one
 * exceeds the budget.</p>
 *
 * <p>Writes {@code target/benchmark/<label>.tsv}, one full
 * {@link GoldenDump} per case under {@code target/benchmark/<label>/dumps/}
 * and {@code dumps.sha256} beside them.</p>
 */
public class ScaleBenchmark {

    private static final String PARALLELISM = "opa.parallelism";
    private static final long SEED = 20260929L;

    @Test
    public void benchmark() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("opa.benchmark"));
        String label = System.getProperty("opa.benchmark.label", "run");
        long budgetMillis = Long.getLong("opa.benchmark.budgetMillis", 600000L);
        boolean keepGoing = Boolean.getBoolean("opa.benchmark.ignoreBudget");
        long timeoutMillis = Long.getLong("opa.benchmark.timeoutMinutes", 0L) * 60000L;
        int repeats = Integer.getInteger("opa.benchmark.repeats", 3);
        int minSize = Integer.getInteger("opa.benchmark.minSize", 0);
        int maxSize = Integer.getInteger("opa.benchmark.maxSize", Integer.MAX_VALUE);
        String paths = System.getProperty("opa.benchmark.parallelism", "both");
        boolean timeDefault = !"serial".equals(paths);
        boolean timeSerial = !"default".equals(paths);
        List<String> families = Arrays.asList(System.getProperty(
                "opa.benchmark.families", "D2,D3,P").split(","));

        File root = new File(projectDir(), "target/benchmark");
        File dumps = new File(root, label + "/dumps");
        if (!dumps.isDirectory() && !dumps.mkdirs()) {
            throw new IOException("Cannot create " + dumps);
        }
        File tsvFile = new File(root, label + ".tsv");
        File sumsFile = new File(root, label + "/dumps.sha256");
        StringBuilder tsv = new StringBuilder();
        tsv.append("case\tfamily\tn_per_channel\tobjects\tthreads\t")
                .append("median_ms_default\truns_default\t")
                .append("median_ms_p1\truns_p1\tpeak_heap_mb\t")
                .append("p1_identical\tdump_sha256\tnote\n");
        StringBuilder sums = new StringBuilder();

        for (Family family : families()) {
            if (!families.contains(family.name)) continue;
            log("family " + family.name + ": warm-up");
            family.run(family.warmUpSize);
            boolean stopped = false;
            for (int n : family.sizes) {
                if (n < minSize || n > maxSize) continue;
                String name = family.name + "_n" + n;
                if (stopped) {
                    row(tsv, name, family.name, n, Timing.NONE, Timing.NONE, "", "",
                            "dropped: previous size exceeded budget");
                    log(name + ": dropped, previous size exceeded budget");
                    continue;
                }
                Timing byDefault = timeDefault
                        ? time(family, n, 0, repeats, budgetMillis, timeoutMillis)
                        : Timing.NONE;
                Timing single = timeSerial && !byDefault.unfinished
                        ? time(family, n, 1, repeats, budgetMillis, timeoutMillis)
                        : Timing.NONE;
                if (byDefault.unfinished || single.unfinished) {
                    String note = "did not finish within "
                            + timeoutMillis / 60000L + " min";
                    row(tsv, name, family.name, n, byDefault, single, "", "", note);
                    log(name + ": " + note);
                    stopped = true;
                    write(tsvFile, tsv, sumsFile, sums);
                    continue;
                }
                String dump = timeDefault ? byDefault.dump : single.dump;
                String identical = timeDefault && timeSerial
                        ? Boolean.toString(byDefault.dump.equals(single.dump))
                        : "not_run";
                String sha = sha256(dump);
                GoldenMasterTest.write(new File(dumps, name + ".txt"), dump);
                sums.append(sha).append("  ").append(name).append(".txt\n");
                row(tsv, name, family.name, n, byDefault, single, identical, sha, "");
                write(tsvFile, tsv, sumsFile, sums);
                log(name + ": default " + byDefault.median() + " ms "
                        + byDefault.millis + ", p1 " + single.median()
                        + " ms " + single.millis + ", identical=" + identical
                        + ", peak heap " + megabytes(byDefault, single) + " MB");
                if (timeDefault && timeSerial) {
                    assertEquals(name + ": parallelism 1 moved the output",
                            byDefault.dump, single.dump);
                }
                if (!keepGoing && (byDefault.medianMillis() > budgetMillis
                        || single.medianMillis() > budgetMillis)) {
                    stopped = true;
                }
            }
        }
        write(tsvFile, tsv, sumsFile, sums);
        log("wrote " + tsvFile);
    }

    private static void row(StringBuilder tsv, String name, String family, int n,
                            Timing byDefault, Timing single, String identical,
                            String sha, String note) {
        boolean measured = byDefault.runs() > 0 || single.runs() > 0;
        tsv.append(name).append('\t')
                .append(family).append('\t')
                .append(n).append('\t')
                .append(2 * n).append('\t')
                .append(measured ? Integer.toString(
                        Runtime.getRuntime().availableProcessors()) : "")
                .append('\t')
                .append(byDefault.median()).append('\t')
                .append(measured ? Integer.toString(byDefault.runs()) : "").append('\t')
                .append(single.median()).append('\t')
                .append(measured ? Integer.toString(single.runs()) : "").append('\t')
                .append(measured ? megabytes(byDefault, single) : "").append('\t')
                .append(identical).append('\t')
                .append(sha).append('\t')
                .append(note).append('\n');
    }

    private static String megabytes(Timing byDefault, Timing single) {
        return String.format(Locale.ROOT, "%.1f",
                Math.max(byDefault.peakBytes, single.peakBytes) / (1024.0 * 1024.0));
    }

    /** Rewritten after every case, so an interrupted run keeps what it measured. */
    private static void write(File tsvFile, StringBuilder tsv,
                              File sumsFile, StringBuilder sums) throws IOException {
        GoldenMasterTest.write(tsvFile, tsv.toString());
        GoldenMasterTest.write(sumsFile, sums.toString());
    }

    // ------------------------------------------------------------- families

    private abstract static class Family {
        final String name;
        final int warmUpSize;
        final int[] sizes;

        Family(String name, int warmUpSize, int... sizes) {
            this.name = name;
            this.warmUpSize = warmUpSize;
            this.sizes = sizes;
        }

        abstract List<ImagePlus> scene(int n);

        abstract void configure(OPAParameters.Builder builder);

        OPAResult run(int n) {
            IJ.resetEscape();
            List<ImagePlus> images = scene(n);
            OPAParameters.Builder builder = OPAParameters.builder(images)
                    .channelNames(Arrays.asList("A", "B"));
            configure(builder);
            return OPA.run(builder.build());
        }
    }

    private static List<Family> families() {
        List<Family> families = new ArrayList<Family>();
        families.add(new Family("D2", 50, 100, 400, 1600) {
            @Override
            List<ImagePlus> scene(int n) {
                return SyntheticScenes.discs(1024, 1024, 4, n, SEED);
            }

            @Override
            void configure(OPAParameters.Builder builder) {
                distances(builder);
            }
        });
        families.add(new Family("D3", 20, 50, 100, 200) {
            @Override
            List<ImagePlus> scene(int n) {
                return SyntheticScenes.balls(256, 256, 48, 5, n, SEED);
            }

            @Override
            void configure(OPAParameters.Builder builder) {
                distances(builder);
            }
        });
        families.add(new Family("P", 100, 200, 1000, 5000) {
            @Override
            List<ImagePlus> scene(int n) {
                return SyntheticScenes.points(2048, 2048, n, SEED);
            }

            @Override
            void configure(OPAParameters.Builder builder) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K,
                                PatternFunction.L,
                                PatternFunction.L_MINUS_R,
                                PatternFunction.PAIR_CORRELATION,
                                PatternFunction.G,
                                PatternFunction.CROSS_K,
                                PatternFunction.CROSS_G))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(null)
                        .radiusBins(50)
                        .maximumRadius(0.0)
                        .simulations(119)
                        .seed(OPAParameters.DEFAULT_SEED)
                        .project3DToXY(true);
            }
        });
        return families;
    }

    private static void distances(OPAParameters.Builder builder) {
        builder.runDistances(true)
                .runPattern(false)
                .includeSelfDistances(true)
                .distanceModes(GoldenConfigurations.MODES_AT_CAPTURE)
                .neighborCount(3)
                .contactDistance(1.0)
                .histogramBins(20);
    }

    // --------------------------------------------------------------- timing

    private static final class Timing {
        /** A path that was not timed: blank median, zero runs. */
        static final Timing NONE = new Timing();

        final List<Long> millis = new ArrayList<Long>();
        boolean unfinished;
        long peakBytes;
        String dump;

        String median() {
            if (millis.isEmpty()) return "";
            List<Long> sorted = new ArrayList<Long>(millis);
            java.util.Collections.sort(sorted);
            return Long.toString(sorted.get(sorted.size() / 2));
        }

        long medianMillis() {
            return millis.isEmpty() ? 0L : Long.parseLong(median());
        }

        int runs() {
            return millis.size();
        }
    }

    /**
     * Times up to {@code repeats} runs. A run longer than the budget ends the
     * repeats; a run longer than the timeout (when set) is cancelled the way a
     * user would cancel it, with Escape, and the timing is marked unfinished.
     */
    private static Timing time(Family family, int n, int parallelism,
                               int repeats, long budgetMillis, long timeoutMillis)
            throws InterruptedException {
        String previous = System.getProperty(PARALLELISM);
        System.setProperty(PARALLELISM, Integer.toString(parallelism));
        Timing timing = new Timing();
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < repeats; i++) {
                System.gc();
                resetPeaks();
                long start = System.nanoTime();
                OPAResult result = run(runner, family, n, timeoutMillis);
                long elapsed = (System.nanoTime() - start) / 1000000L;
                if (result == null) {
                    timing.unfinished = true;
                    break;
                }
                timing.millis.add(elapsed);
                timing.peakBytes = Math.max(timing.peakBytes, peakHeap());
                String dump = GoldenDump.of(result);
                if (timing.dump == null) {
                    timing.dump = dump;
                } else {
                    assertEquals(family.name + "_n" + n
                            + ": repeated run moved the output",
                            timing.dump, dump);
                }
                // One over-budget run is measurement enough.
                if (elapsed > budgetMillis) break;
            }
        } finally {
            runner.shutdownNow();
            if (previous == null) {
                System.clearProperty(PARALLELISM);
            } else {
                System.setProperty(PARALLELISM, previous);
            }
        }
        return timing;
    }

    /** The result, or null when the run was cancelled at the timeout. */
    private static OPAResult run(ExecutorService runner, final Family family,
                                 final int n, long timeoutMillis)
            throws InterruptedException {
        Future<OPAResult> future = runner.submit(new Callable<OPAResult>() {
            @Override
            public OPAResult call() {
                return family.run(n);
            }
        });
        try {
            return timeoutMillis > 0
                    ? future.get(timeoutMillis, TimeUnit.MILLISECONDS)
                    : future.get();
        } catch (TimeoutException tooLong) {
            IJ.setKeyDown(KeyEvent.VK_ESCAPE);
            try {
                future.get();
            } catch (ExecutionException cancelled) {
                // Expected: the engine stops with AnalysisCancelledException.
            } finally {
                IJ.resetEscape();
            }
            return null;
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }

    private static void resetPeaks() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) pool.resetPeakUsage();
        }
    }

    private static long peakHeap() {
        long total = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                total += pool.getPeakUsage().getUsed();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- utils

    static String sha256(String text) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }

    private static File projectDir() {
        String basedir = System.getProperty("basedir");
        return new File(basedir == null
                ? System.getProperty("user.dir") : basedir);
    }

    private static void log(String message) {
        System.out.println("[benchmark] " + message);
        System.out.flush();
    }
}
