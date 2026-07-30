/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import java.io.File;

/**
 * Immutable folder-batch configuration.
 */
public final class OPABatchParameters {

    private final File inputFolder;
    private final String filenameRegex;
    private final int channelCaptureGroup;
    private final boolean recursive;
    private final OPAParameters analysisTemplate;
    private final boolean autoSave;
    private final File outputDirectory;

    private OPABatchParameters(Builder builder) {
        this.inputFolder = builder.inputFolder;
        this.filenameRegex = builder.filenameRegex;
        this.channelCaptureGroup = builder.channelCaptureGroup;
        this.recursive = builder.recursive;
        this.analysisTemplate = builder.analysisTemplate;
        this.autoSave = builder.autoSave;
        this.outputDirectory = builder.outputDirectory;
    }

    public static Builder builder(File inputFolder,
                                  String filenameRegex,
                                  int channelCaptureGroup) {
        return new Builder(inputFolder, filenameRegex, channelCaptureGroup);
    }

    public File getInputFolder() {
        return inputFolder;
    }

    public String getFilenameRegex() {
        return filenameRegex;
    }

    public int getChannelCaptureGroup() {
        return channelCaptureGroup;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public OPAParameters getAnalysisTemplate() {
        return analysisTemplate;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public static final class Builder {
        private final File inputFolder;
        private final String filenameRegex;
        private final int channelCaptureGroup;
        private boolean recursive = true;
        private OPAParameters analysisTemplate =
                OPAParameters.builder().build();
        private boolean autoSave = true;
        private File outputDirectory;

        private Builder(File inputFolder,
                        String filenameRegex,
                        int channelCaptureGroup) {
            this.inputFolder = inputFolder;
            this.filenameRegex = filenameRegex;
            this.channelCaptureGroup = channelCaptureGroup;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder analysisTemplate(OPAParameters analysisTemplate) {
            this.analysisTemplate = analysisTemplate;
            return this;
        }

        public Builder autoSave(boolean autoSave) {
            this.autoSave = autoSave;
            return this;
        }

        public Builder outputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public OPABatchParameters build() {
            return new OPABatchParameters(this);
        }
    }
}
