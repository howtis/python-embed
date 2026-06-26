package io.github.howtis.pythonembed.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Generates {@code META-INF/python-embed.properties} for runtime discovery
 * of the Python environment path.
 *
 * <p>Automatically runs after the {@code setup} goal via {@code @Execute}.
 * The generated properties file is placed in {@code ${project.build.outputDirectory}}
 * so it is included in the classpath at runtime.
 */
@Mojo(name = "properties", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
@Execute(goal = "setup")
public class PropertiesMojo extends AbstractMojo {

    static final String PROPS_RESOURCE_PATH = "META-INF/python-embed.properties";
    static final String PROPS_KEY_VENV_PATH = "venv.path";
    static final String PROPS_KEY_VENV_EMBEDDED = "venv.embedded";

    /**
     * Path to the venv output directory.
     */
    @Parameter(property = "python-embed.venvOutputDir",
            defaultValue = "${project.build.directory}/python-venv")
    private File venvOutputDir;

    /**
     * The Maven project (auto-injected).
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Skip the properties generation.
     */
    @Parameter(property = "python-embed.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping python-embed:properties (skip=true)");
            return;
        }

        Path venvPath = venvOutputDir.toPath().toAbsolutePath();
        Path buildDir = Path.of(project.getBuild().getDirectory()).toAbsolutePath();
        boolean embedded = venvPath.startsWith(buildDir);

        Properties props = new Properties();
        props.setProperty(PROPS_KEY_VENV_PATH, venvPath.toString());
        props.setProperty(PROPS_KEY_VENV_EMBEDDED, String.valueOf(embedded));

        Path outputDir = Path.of(project.getBuild().getOutputDirectory());
        Path outputFile = outputDir.resolve(PROPS_RESOURCE_PATH);

        try {
            Files.createDirectories(outputFile.getParent());
            try (var writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                props.store(writer, "PythonEmbed runtime configuration");
            }
            getLog().info("Generated: " + outputFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + PROPS_RESOURCE_PATH, e);
        }
    }
}
