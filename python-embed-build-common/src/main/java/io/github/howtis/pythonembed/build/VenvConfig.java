package io.github.howtis.pythonembed.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration for Python environment setup.
 * Use {@link #builder()} for fluent construction.
 */
public final class VenvConfig {

    private final List<String> packages;
    private final String pythonVersion;
    private final Path requirementsFile;
    private final Path pyprojectTomlFile;
    private final String pipIndexUrl;
    private final List<String> pipExtraArgs;
    private final Path venvDir;
    private final String targetOs;
    private final Consumer<String> logger;

    private VenvConfig(Builder builder) {
        this.packages = Collections.unmodifiableList(new ArrayList<>(builder.packages));
        this.pythonVersion = builder.pythonVersion;
        this.requirementsFile = builder.requirementsFile;
        this.pyprojectTomlFile = builder.pyprojectTomlFile;
        this.pipIndexUrl = builder.pipIndexUrl;
        this.pipExtraArgs = Collections.unmodifiableList(new ArrayList<>(builder.pipExtraArgs));
        this.venvDir = Objects.requireNonNull(builder.venvDir, "venvDir must not be null");
        this.targetOs = builder.targetOs;
        this.logger = builder.logger;
    }

    public List<String> packages() { return packages; }
    public String pythonVersion() { return pythonVersion; }
    public Path requirementsFile() { return requirementsFile; }
    public Path pyprojectTomlFile() { return pyprojectTomlFile; }
    public String pipIndexUrl() { return pipIndexUrl; }
    public List<String> pipExtraArgs() { return pipExtraArgs; }
    public Path venvDir() { return venvDir; }
    public String targetOs() { return targetOs; }
    public Consumer<String> logger() { return logger; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> packages = List.of();
        private String pythonVersion = "3.12";
        private Path requirementsFile;
        private Path pyprojectTomlFile;
        private String pipIndexUrl;
        private List<String> pipExtraArgs = List.of();
        private Path venvDir;
        private String targetOs;
        private Consumer<String> logger = s -> {};

        private Builder() {}

        public Builder packages(List<String> packages) {
            this.packages = Objects.requireNonNull(packages, "packages must not be null");
            return this;
        }

        public Builder pythonVersion(String pythonVersion) {
            this.pythonVersion = Objects.requireNonNull(pythonVersion, "pythonVersion must not be null");
            return this;
        }

        public Builder requirementsFile(Path requirementsFile) {
            this.requirementsFile = requirementsFile;
            return this;
        }

        public Builder pyprojectTomlFile(Path pyprojectTomlFile) {
            this.pyprojectTomlFile = pyprojectTomlFile;
            return this;
        }

        public Builder pipIndexUrl(String pipIndexUrl) {
            this.pipIndexUrl = pipIndexUrl;
            return this;
        }

        public Builder pipExtraArgs(List<String> pipExtraArgs) {
            this.pipExtraArgs = Objects.requireNonNull(pipExtraArgs, "pipExtraArgs must not be null");
            return this;
        }

        public Builder venvDir(Path venvDir) {
            this.venvDir = Objects.requireNonNull(venvDir, "venvDir must not be null");
            return this;
        }

        public Builder targetOs(String targetOs) {
            this.targetOs = targetOs;
            return this;
        }

        public Builder logger(Consumer<String> logger) {
            this.logger = Objects.requireNonNull(logger, "logger must not be null");
            return this;
        }

        public VenvConfig build() {
            return new VenvConfig(this);
        }
    }
}
