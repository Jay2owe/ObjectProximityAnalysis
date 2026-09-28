/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Shared settings, summary statistics and report writing for the validation
 * tests.
 *
 * <p>Two modes. The default is a fast mode that runs inside every
 * {@code mvn verify}; {@code -Dopa.validation=true} switches to the full
 * realisation counts the validation plan specifies. Both are seeded, so a run
 * is a pure function of its settings.</p>
 */
final class ValidationSupport {

    static final boolean FULL = Boolean.getBoolean("opa.validation");
    static final long SEED = Long.getLong("opa.validation.seed", 20260928L);

    private ValidationSupport() {
    }

    static int realisations(int fast, int full) {
        return Integer.getInteger("opa.validation.realisations", FULL ? full : fast);
    }

    static String mode() {
        return FULL ? "full" : "fast";
    }

    /** Deterministic per-task seed, independent of thread scheduling. */
    static long seed(long caseIndex, long realisation) {
        return SEED + 1_000_003L * caseIndex + 7_919L * realisation;
    }

    static double mean(double[][] rows, int column) {
        double sum = 0.0;
        for (double[] row : rows) sum += row[column];
        return sum / rows.length;
    }

    static double variance(double[][] rows, int column) {
        double mean = mean(rows, column);
        double sum = 0.0;
        for (double[] row : rows) {
            double d = row[column] - mean;
            sum += d * d;
        }
        return rows.length < 2 ? 0.0 : sum / (rows.length - 1);
    }

    static double standardError(double[][] rows, int column) {
        return Math.sqrt(variance(rows, column) / rows.length);
    }

    /** Wilson score interval for a binomial proportion, 95%. */
    static double[] wilson(int successes, int trials) {
        double z = 1.959963984540054;
        double p = successes / (double) trials;
        double denominator = 1.0 + z * z / trials;
        double centre = (p + z * z / (2.0 * trials)) / denominator;
        double half = z * Math.sqrt(p * (1.0 - p) / trials
                + z * z / (4.0 * trials * trials)) / denominator;
        return new double[]{Math.max(0.0, centre - half), Math.min(1.0, centre + half)};
    }

    /**
     * The z such that P(|Z| > z) = alpha for a standard normal Z, by bisection
     * on the complementary error function.
     */
    static double twoSidedNormalQuantile(double alpha) {
        double low = 0.0;
        double high = 40.0;
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (low + high);
            if (erfc(mid / Math.sqrt(2.0)) > alpha) low = mid; else high = mid;
        }
        return 0.5 * (low + high);
    }

    /** Complementary error function, fractional error below 1.2e-7. */
    static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0 / (1.0 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368
                + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806
                + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0.0 ? r : 2.0 - r;
    }

    static String format(String pattern, Object... values) {
        return String.format(Locale.ROOT, pattern, values);
    }

    /** Runs tasks on a small pool and returns results in submission order. */
    static <T> List<T> runAll(List<Callable<T>> tasks) {
        int workers = Math.max(1, Math.min(8,
                Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
            for (Callable<T> task : tasks) futures.add(executor.submit(task));
            List<T> results = new ArrayList<T>(tasks.size());
            for (Future<T> future : futures) results.add(future.get());
            return results;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException(failed.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    static Path writeReport(String name, CharSequence text) {
        try {
            Path directory = Paths.get("target", "validation");
            Files.createDirectories(directory);
            Path file = directory.resolve(name);
            Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException failed) {
            throw new IllegalStateException("Could not write " + name, failed);
        }
    }
}
