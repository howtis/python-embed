package io.github.howtis.pythonembed.maven;

import io.github.howtis.pythonembed.build.VenvConfig;
import io.github.howtis.pythonembed.build.VenvManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a Python virtual environment (venv) and installs specified packages.
 *
 * <p>If system Python is available, a standard venv is used.
 * If not, a portable CPython is auto-downloaded from python-build-standalone.
 *
 * <p>Skips execution when {@code skip} is set to {@code true}.
 *
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.howtis</groupId>
 *   <artifactId>python-embed-maven-plugin</artifactId>
 *   <version>1.0.2</version>
 *   <executions>
 *     <execution>
 *       <goals><goal>setup</goal></goals>
 *     </execution>
 *   </executions>
 *   <configuration>
 *     <packages>
 *       <package>numpy==1.26.4</package>
 *     </packages>
 *   </configuration>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "setup", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class SetupMojo extends AbstractMojo {

    /**
     * List of pip packages to install.
     */
    @Parameter(property = "python-embed.packages")
    private List<String> packages = new ArrayList<>();

    /**
     * Python version to download when system Python is not available.
     */
    @Parameter(property = "python-embed.pythonVersion", defaultValue = "3.12")
    private String pythonVersion;

    /**
     * Path to the venv output directory.
     */
    @Parameter(property = "python-embed.venvOutputDir",
            defaultValue = "${project.build.directory}/python-venv")
    private File venvOutputDir;

    /**
     * Optional path to requirements.txt (relative to project base directory).
     */
    @Parameter(property = "python-embed.requirementsFile")
    private File requirementsFile;

    /**
     * Optional path to pyproject.toml (relative to project base directory).
     */
    @Parameter(property = "python-embed.pyprojectTomlFile")
    private File pyprojectTomlFile;

    /**
     * Optional pip index URL (e.g., PyTorch CUDA index).
     */
    @Parameter(property = "python-embed.pipIndexUrl")
    private String pipIndexUrl;

    /**
     * Optional extra pip install arguments (e.g., --extra-index-url, -f).
     */
    @Parameter(property = "python-embed.pipExtraArgs")
    private List<String> pipExtraArgs = new ArrayList<>();

    /**
     * Skip the plugin execution entirely.
     */
    @Parameter(property = "python-embed.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Target OS for cross-compilation: "windows", "linux", "macos" (null = auto-detect).
     */
    @Parameter(property = "python-embed.targetOs")
    private String targetOs;

    /**
     * The Maven project (auto-injected).
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The Maven session (auto-injected).
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping python-embed:setup (skip=true)");
            return;
        }

        Path projectRoot = project.getBasedir().toPath();
        Path venvDir = venvOutputDir.toPath();

        Path requirementsPath = null;
        Path pyprojectPath = null;

        if (requirementsFile != null) {
            requirementsPath = requirementsFile.toPath();
            if (!requirementsPath.isAbsolute()) {
                requirementsPath = projectRoot.resolve(requirementsPath);
            }
        }
        if (pyprojectTomlFile != null) {
            pyprojectPath = pyprojectTomlFile.toPath();
            if (!pyprojectPath.isAbsolute()) {
                pyprojectPath = projectRoot.resolve(pyprojectPath);
            }
        }

        VenvConfig config = VenvConfig.builder()
                .packages(packages)
                .pythonVersion(pythonVersion)
                .requirementsFile(requirementsPath)
                .pyprojectTomlFile(pyprojectPath)
                .pipIndexUrl(pipIndexUrl)
                .pipExtraArgs(pipExtraArgs)
                .venvDir(venvDir)
                .targetOs(targetOs)
                .logger(msg -> getLog().info(msg))
                .build();

        try {
            VenvManager.setup(config);
        } catch (IOException e) {
            throw new MojoExecutionException("Python environment setup failed: " + e.getMessage(), e);
        }
    }
}
