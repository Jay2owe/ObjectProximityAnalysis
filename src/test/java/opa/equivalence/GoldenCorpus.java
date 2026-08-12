/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic synthetic label images for the pre-extraction golden master.
 *
 * <p>Every case is rebuilt from code on every run, so the corpus cannot drift
 * and no binary fixtures are committed. Sizes are deliberately small: the
 * distance engine is quadratic in both object count and per-object surface
 * size.</p>
 */
final class GoldenCorpus {

    private GoldenCorpus() {
    }

    /** One corpus entry: a name and a factory for its channel images. */
    abstract static class Case {

        private final String name;
        private final List<String> channelNames;

        Case(String name, String... channelNames) {
            this.name = name;
            this.channelNames = Arrays.asList(channelNames);
        }

        String getName() {
            return name;
        }

        List<String> getChannelNames() {
            return channelNames;
        }

        /** Fresh images on every call. */
        abstract List<ImagePlus> images();
    }

    static List<Case> cases() {
        List<Case> cases = new ArrayList<Case>();

        cases.add(new Case("empty_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                return one(calibrated(image("A", 12, 12, 1, 8)));
            }
        });

        cases.add(new Case("single_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                box(a, 4, 4, 0, 3, 3, 1, 1);
                return one(a);
            }
        });

        cases.add(new Case("pair_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                box(a, 1, 1, 0, 2, 2, 1, 1);
                box(a, 8, 8, 0, 2, 2, 1, 2);
                return one(a);
            }
        });

        cases.add(new Case("touching_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 2, 2, 0, 3, 3, 1, 1);
                box(b, 5, 2, 0, 3, 3, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("overlap_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 2, 2, 0, 4, 4, 1, 1);
                box(b, 3, 3, 0, 4, 4, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("nothing_matches_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 0, 0, 0, 2, 2, 1, 1);
                box(b, 10, 10, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("everything_matches_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 1, 8));
                ImagePlus b = calibrated(image("B", 8, 8, 1, 8));
                box(a, 0, 0, 0, 8, 8, 1, 1);
                box(b, 0, 0, 0, 8, 8, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("edge_objects_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 0, 0, 0, 2, 2, 1, 1);
                box(a, 5, 5, 0, 2, 2, 1, 2);
                box(b, 10, 0, 0, 2, 2, 1, 1);
                box(b, 0, 10, 0, 2, 2, 1, 2);
                return two(a, b);
            }
        });

        cases.add(new Case("regular_grid_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                int label = 1;
                for (int gy = 0; gy < 3; gy++) {
                    for (int gx = 0; gx < 3; gx++) {
                        box(a, 1 + gx * 4, 1 + gy * 4, 0, 2, 2, 1, label++);
                    }
                }
                return one(a);
            }
        });

        cases.add(new Case("clustered_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                int label = 1;
                for (int gy = 0; gy < 3; gy++) {
                    for (int gx = 0; gx < 3; gx++) {
                        set(a, 1 + gx * 2, 1 + gy * 2, 0, label++);
                    }
                }
                return one(a);
            }
        });

        cases.add(new Case("three_channel_2d", "A", "B", "C") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                ImagePlus c = calibrated(image("C", 12, 12, 1, 8));
                box(a, 1, 1, 0, 2, 2, 1, 1);
                box(a, 8, 1, 0, 2, 2, 1, 2);
                box(b, 1, 8, 0, 2, 2, 1, 1);
                box(b, 5, 5, 0, 2, 2, 1, 2);
                box(c, 8, 8, 0, 2, 2, 1, 1);
                box(c, 4, 1, 0, 2, 2, 1, 2);
                List<ImagePlus> images = new ArrayList<ImagePlus>();
                images.add(a);
                images.add(b);
                images.add(c);
                return images;
            }
        });

        cases.add(new Case("five_channel_2d", "A", "B", "C", "D", "E") {
            @Override
            List<ImagePlus> images() {
                List<ImagePlus> images = new ArrayList<ImagePlus>();
                String[] titles = {"A", "B", "C", "D", "E"};
                for (int i = 0; i < titles.length; i++) {
                    ImagePlus image = calibrated(image(titles[i], 12, 12, 1, 8));
                    box(image, 1 + i * 2, 1 + i * 2, 0, 2, 2, 1, 1);
                    images.add(image);
                }
                return images;
            }
        });

        cases.add(new Case("duplicate_names_2ch_2d", "Same", "Same") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("Same", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("Same", 12, 12, 1, 8));
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 7, 7, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("single_3d_1ch", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 3, 8));
                box(a, 3, 3, 1, 2, 2, 2, 1);
                return one(a);
            }
        });

        cases.add(new Case("pair_3d_2ch", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 3, 8));
                ImagePlus b = calibrated(image("B", 8, 8, 3, 8));
                box(a, 1, 1, 0, 2, 2, 2, 1);
                box(a, 5, 5, 1, 2, 2, 2, 2);
                box(b, 1, 4, 0, 2, 2, 3, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("full_depth_3d_2ch", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 3, 8));
                ImagePlus b = calibrated(image("B", 8, 8, 3, 8));
                box(a, 2, 2, 0, 2, 2, 3, 1);
                box(b, 4, 2, 0, 2, 2, 3, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("anisotropic_3d_2ch", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrate(image("A", 8, 8, 3, 8), 0.2, 0.2, 1.0, "micron");
                ImagePlus b = calibrate(image("B", 8, 8, 3, 8), 0.2, 0.2, 1.0, "micron");
                box(a, 1, 1, 0, 2, 2, 2, 1);
                box(b, 4, 4, 1, 2, 2, 2, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("nondyadic_calibration_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrate(image("A", 12, 12, 1, 8), 0.1625, 0.1625, 0.1625, "micron");
                ImagePlus b = calibrate(image("B", 12, 12, 1, 8), 0.1625, 0.1625, 0.1625, "micron");
                box(a, 2, 2, 0, 3, 3, 1, 1);
                box(b, 5, 2, 0, 3, 3, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("uncalibrated_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = image("A", 12, 12, 1, 8);
                ImagePlus b = image("B", 12, 12, 1, 8);
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 7, 7, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("origin_shift_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = origin(calibrated(image("A", 12, 12, 1, 8)), 2.0, 3.0, 0.0);
                ImagePlus b = origin(calibrated(image("B", 12, 12, 1, 8)), 2.0, 3.0, 0.0);
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 7, 7, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("bitdepth_8_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                set(a, 1, 1, 0, 1);
                set(a, 5, 5, 0, 128);
                set(a, 9, 9, 0, 255);
                set(b, 2, 2, 0, 254);
                return two(a, b);
            }
        });

        cases.add(new Case("bitdepth_16_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 16));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 16));
                set(a, 1, 1, 0, 65533);
                set(a, 5, 5, 0, 65534);
                set(a, 9, 9, 0, 65535);
                set(b, 2, 2, 0, 256);
                return two(a, b);
            }
        });

        cases.add(new Case("bitdepth_32_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 32));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 32));
                set(a, 1, 1, 0, 1);
                set(a, 5, 5, 0, 70000);
                set(b, 2, 2, 0, 3);
                return two(a, b);
            }
        });

        cases.add(new Case("many_objects_1ch_2d", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 16));
                int label = 1;
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        set(a, 1 + x * 3, 1 + y * 3, 0, label++);
                    }
                }
                return one(a);
            }
        });

        cases.add(new Case("empty_target_2ch_2d", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(a, 8, 8, 0, 2, 2, 1, 2);
                return two(a, b);
            }
        });

        cases.add(new Case("reject_mismatched_dimensions", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 12, 12, 1, 8));
                ImagePlus b = calibrated(image("B", 10, 10, 1, 8));
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 2, 2, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("reject_mismatched_calibration", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrate(image("A", 12, 12, 1, 8), 1.0, 1.0, 1.0, "micron");
                ImagePlus b = calibrate(image("B", 12, 12, 1, 8), 0.5, 0.5, 1.0, "micron");
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 2, 2, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("reject_mismatched_origin", "A", "B") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = origin(calibrated(image("A", 12, 12, 1, 8)), 1.0, 0.0, 0.0);
                ImagePlus b = calibrated(image("B", 12, 12, 1, 8));
                box(a, 2, 2, 0, 2, 2, 1, 1);
                box(b, 2, 2, 0, 2, 2, 1, 1);
                return two(a, b);
            }
        });

        cases.add(new Case("reject_hyperstack", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 2, 8));
                box(a, 2, 2, 0, 2, 2, 2, 1);
                a.setDimensions(2, 1, 1);
                return one(a);
            }
        });

        cases.add(new Case("reject_noninteger_label", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 1, 32));
                a.getStack().getProcessor(1).setf(3, 3, 1.5f);
                return one(a);
            }
        });

        cases.add(new Case("reject_negative_label", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = calibrated(image("A", 8, 8, 1, 32));
                a.getStack().getProcessor(1).setf(3, 3, -2.0f);
                return one(a);
            }
        });

        cases.add(new Case("reject_zero_pixel_width", "A") {
            @Override
            List<ImagePlus> images() {
                ImagePlus a = image("A", 8, 8, 1, 8);
                Calibration calibration = new Calibration();
                calibration.pixelWidth = 0.0;
                calibration.pixelHeight = 1.0;
                calibration.pixelDepth = 1.0;
                calibration.setUnit("micron");
                a.setCalibration(calibration);
                box(a, 2, 2, 0, 2, 2, 1, 1);
                return one(a);
            }
        });

        return cases;
    }

    // ---------------------------------------------------------------- images

    static ImagePlus image(String title, int width, int height, int depth, int bitDepth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice(processor(width, height, bitDepth));
        }
        return new ImagePlus(title, stack);
    }

    private static ImageProcessor processor(int width, int height, int bitDepth) {
        if (bitDepth == 8) return new ByteProcessor(width, height);
        if (bitDepth == 16) return new ShortProcessor(width, height);
        if (bitDepth == 32) return new FloatProcessor(width, height);
        throw new IllegalArgumentException("Unsupported bit depth: " + bitDepth);
    }

    static void set(ImagePlus image, int x, int y, int z, int label) {
        ImageProcessor processor = image.getStack().getProcessor(z + 1);
        if (processor instanceof FloatProcessor) {
            processor.setf(x, y, (float) label);
        } else {
            processor.set(x, y, label);
        }
    }

    static void box(ImagePlus image,
                    int x0, int y0, int z0,
                    int width, int height, int depth,
                    int label) {
        for (int z = z0; z < z0 + depth; z++) {
            for (int y = y0; y < y0 + height; y++) {
                for (int x = x0; x < x0 + width; x++) {
                    set(image, x, y, z, label);
                }
            }
        }
    }

    static ImagePlus calibrated(ImagePlus image) {
        return calibrate(image, 0.5, 0.5, 0.5, "micron");
    }

    static ImagePlus calibrate(ImagePlus image,
                               double pixelWidth,
                               double pixelHeight,
                               double pixelDepth,
                               String unit) {
        Calibration calibration = new Calibration();
        calibration.pixelWidth = pixelWidth;
        calibration.pixelHeight = pixelHeight;
        calibration.pixelDepth = pixelDepth;
        calibration.setUnit(unit);
        image.setCalibration(calibration);
        return image;
    }

    static ImagePlus origin(ImagePlus image, double x, double y, double z) {
        Calibration calibration = image.getCalibration();
        calibration.xOrigin = x;
        calibration.yOrigin = y;
        calibration.zOrigin = z;
        image.setCalibration(calibration);
        return image;
    }

    private static List<ImagePlus> one(ImagePlus image) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(image);
        return images;
    }

    private static List<ImagePlus> two(ImagePlus first, ImagePlus second) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(first);
        images.add(second);
        return images;
    }
}
