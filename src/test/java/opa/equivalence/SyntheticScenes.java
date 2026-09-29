/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded synthetic label scenes for the scale benchmark. Objects within one
 * channel never overlap; objects in different channels may.
 */
final class SyntheticScenes {

    private SyntheticScenes() {
    }

    /** Two channels of 2D discs, calibrated 0.2 x 0.2 micron. */
    static List<ImagePlus> discs(int width, int height, int radius,
                                 int perChannel, long seed) {
        List<ImagePlus> channels = new ArrayList<ImagePlus>();
        for (int channel = 0; channel < 2; channel++) {
            Random random = new Random(seed * 31 + channel);
            ShortProcessor processor = new ShortProcessor(width, height);
            List<int[]> centres = new ArrayList<int[]>();
            int minimum = 2 * radius + 2;
            int attempts = 0;
            while (centres.size() < perChannel) {
                if (++attempts > perChannel * 10000) {
                    throw new IllegalStateException("scene too dense");
                }
                int x = radius + random.nextInt(width - 2 * radius);
                int y = radius + random.nextInt(height - 2 * radius);
                if (tooClose(centres, x, y, 0, minimum, 1.0)) continue;
                centres.add(new int[]{x, y, 0});
                int label = centres.size();
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (dx * dx + dy * dy <= radius * radius) {
                            processor.set(x + dx, y + dy, label);
                        }
                    }
                }
            }
            channels.add(calibrate(new ImagePlus(
                    "D" + (channel + 1), processor), 0.2, 0.2, 1.0));
        }
        return channels;
    }

    /** Two channels of 3D balls, calibrated 0.2 x 0.2 x 0.5 micron. */
    static List<ImagePlus> balls(int width, int height, int depth, int radius,
                                 int perChannel, long seed) {
        List<ImagePlus> channels = new ArrayList<ImagePlus>();
        for (int channel = 0; channel < 2; channel++) {
            Random random = new Random(seed * 31 + channel);
            ImageStack stack = new ImageStack(width, height);
            for (int z = 0; z < depth; z++) {
                stack.addSlice(new ShortProcessor(width, height));
            }
            List<int[]> centres = new ArrayList<int[]>();
            int minimum = 2 * radius + 2;
            int attempts = 0;
            while (centres.size() < perChannel) {
                if (++attempts > perChannel * 10000) {
                    throw new IllegalStateException("scene too dense");
                }
                int x = radius + random.nextInt(width - 2 * radius);
                int y = radius + random.nextInt(height - 2 * radius);
                int z = radius + random.nextInt(depth - 2 * radius);
                if (tooClose(centres, x, y, z, minimum, 1.0)) continue;
                centres.add(new int[]{x, y, z});
                int label = centres.size();
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                                stack.getProcessor(z + dz + 1)
                                        .set(x + dx, y + dy, label);
                            }
                        }
                    }
                }
            }
            channels.add(calibrate(new ImagePlus(
                    "B" + (channel + 1), stack), 0.2, 0.2, 0.5));
        }
        return channels;
    }

    /** Two channels of single-pixel objects at distinct positions. */
    static List<ImagePlus> points(int width, int height, int perChannel,
                                  long seed) {
        List<ImagePlus> channels = new ArrayList<ImagePlus>();
        for (int channel = 0; channel < 2; channel++) {
            Random random = new Random(seed * 31 + channel);
            ShortProcessor processor = new ShortProcessor(width, height);
            int label = 0;
            while (label < perChannel) {
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                if (processor.get(x, y) != 0) continue;
                processor.set(x, y, ++label);
            }
            channels.add(calibrate(new ImagePlus(
                    "P" + (channel + 1), processor), 0.2, 0.2, 1.0));
        }
        return channels;
    }

    private static boolean tooClose(List<int[]> centres, int x, int y, int z,
                                    int minimum, double zScale) {
        long limit = (long) minimum * minimum;
        for (int[] c : centres) {
            long dx = c[0] - x;
            long dy = c[1] - y;
            long dz = Math.round((c[2] - z) * zScale);
            if (dx * dx + dy * dy + dz * dz < limit) return true;
        }
        return false;
    }

    private static ImagePlus calibrate(ImagePlus image, double width,
                                       double height, double depth) {
        Calibration calibration = new Calibration();
        calibration.pixelWidth = width;
        calibration.pixelHeight = height;
        calibration.pixelDepth = depth;
        calibration.setUnit("micron");
        image.setCalibration(calibration);
        return image;
    }
}
