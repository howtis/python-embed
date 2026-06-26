package io.github.howtis.pythonembed.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Display help information on python-embed-maven-plugin.
 *
 * <pre>{@code
 * mvn python-embed:help -Ddetail=true -Dgoal=setup
 * }</pre>
 */
@Mojo(name = "help", threadSafe = true)
public class HelpMojo extends AbstractMojo {

    /**
     * If true, display all settable properties for each mojo.
     */
    @Parameter(property = "detail", defaultValue = "false")
    private boolean detail;

    /**
     * The name of the mojo for which to show help.
     */
    @Parameter(property = "goal")
    private String goal;

    /**
     * The number of spaces per indentation level.
     */
    @Parameter(property = "indentSize", defaultValue = "2")
    private int indentSize;

    /**
     * The maximum line length for display.
     */
    @Parameter(property = "lineLength", defaultValue = "80")
    private int lineLength;

    private static final String HELP_TEXT = """
            python-embed-maven-plugin %s

            This plugin creates a Python virtual environment (venv) and installs
            specified packages at build time. If system Python is not available,
            a portable CPython is automatically downloaded from python-build-standalone.

            Goals:

              setup       Creates a Python venv and installs packages.
                          Default phase: generate-resources
                          Usage: mvn python-embed:setup

              properties  Generates META-INF/python-embed.properties for runtime discovery.
                          Default phase: generate-resources
                          Usage: mvn python-embed:properties

              help        Display this help message.
                          Usage: mvn python-embed:help [-Ddetail=true] [-Dgoal=<goal>]

            Configuration (setup goal):

              packages           List of pip packages to install
              pythonVersion      Python version for auto-download (default: 3.12)
              venvOutputDir      Venv output directory (default: ${project.build.directory}/python-venv)
              requirementsFile   Path to requirements.txt
              pyprojectTomlFile  Path to pyproject.toml
              pipIndexUrl        Custom pip index URL
              pipExtraArgs       Extra pip install arguments
              skip               Skip plugin execution (default: false)
              targetOs           Target OS: windows, linux, macos (null = auto-detect)

            For more information, visit: https://github.com/howtis/python-embed
            """;

    @Override
    public void execute() throws MojoExecutionException {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            version = "1.0.2";
        }

        if (detail) {
            String targetGoal = (goal != null && !goal.isEmpty()) ? goal : "setup";
            getLog().info(buildDetailHelp(version, targetGoal));
        } else {
            getLog().info(String.format(HELP_TEXT, version));
        }
    }

    private String buildDetailHelp(String version, String goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("python-embed-maven-plugin ").append(version).append("\n");
        sb.append("\n");
        sb.append("Goal: ").append(goal).append("\n");
        sb.append("=".repeat(Math.min(sb.length(), lineLength)));
        sb.append("\n\n");

        if ("setup".equals(goal)) {
            sb.append(indent("packages", "List of pip packages to install (String[])", indentSize));
            sb.append(indent("pythonVersion", "Python version for auto-download (default: 3.12)", indentSize));
            sb.append(indent("venvOutputDir", "Venv output directory (default: ${project.build.directory}/python-venv)", indentSize));
            sb.append(indent("requirementsFile", "Path to requirements.txt", indentSize));
            sb.append(indent("pyprojectTomlFile", "Path to pyproject.toml", indentSize));
            sb.append(indent("pipIndexUrl", "Custom pip index URL", indentSize));
            sb.append(indent("pipExtraArgs", "Extra pip install arguments (String[])", indentSize));
            sb.append(indent("skip", "Skip plugin execution (default: false)", indentSize));
            sb.append(indent("targetOs", "Target OS: windows, linux, macos (auto-detect if not set)", indentSize));
        } else if ("properties".equals(goal)) {
            sb.append(indent("skip", "Skip plugin execution (default: false)", indentSize));
        } else if ("help".equals(goal)) {
            sb.append(indent("detail", "Display all settable properties (default: false)", indentSize));
            sb.append(indent("goal", "Goal name to show detailed help for", indentSize));
            sb.append(indent("indentSize", "Indentation size (default: 2)", indentSize));
            sb.append(indent("lineLength", "Maximum line length (default: 80)", indentSize));
        }

        return sb.toString();
    }

    private static String indent(String name, String description, int size) {
        return " ".repeat(size) + name + "\n" + " ".repeat(size * 2) + description + "\n\n";
    }
}
