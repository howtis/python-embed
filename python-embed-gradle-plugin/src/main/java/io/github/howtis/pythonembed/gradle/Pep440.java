package io.github.howtis.pythonembed.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEP 440 version and version specifier utilities.
 *
 * <p>PEP 440 defines the version identification and dependency specification
 * scheme for Python packages. This class provides validation and parsing
 * for PEP 440 version strings and specifiers.
 *
 * <p>Supported specifier operators: ==, !=, >=, <=, &gt;, &lt;, ~=, ===
 *
 * @see <a href="https://peps.python.org/pep-0440/">PEP 440</a>
 */
public final class Pep440 {

    // Epoch segment: optional non-zero digit followed by !
    private static final String EPOCH = "(?:[1-9][0-9]*!)?";

    // Release segment: N(.N)*
    private static final String RELEASE = "(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))*";

    // Pre-release: {a|b|rc}N
    private static final String PRE_RELEASE = "(?:(?:a|b|rc)(?:0|[1-9][0-9]*))?";

    // Post-release: .postN
    private static final String POST_RELEASE = "(?:\\.post(?:0|[1-9][0-9]*))?";

    // Dev release: .devN
    private static final String DEV_RELEASE = "(?:\\.dev(?:0|[1-9][0-9]*))?";

    // Local version: +local
    private static final String LOCAL = "(?:\\+[a-zA-Z0-9]+(?:\\.[a-zA-Z0-9]+)*)?";

    // Full PEP 440 public version pattern
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^" + EPOCH + RELEASE + PRE_RELEASE + POST_RELEASE + DEV_RELEASE + LOCAL + "$");

    // Package name: alphanumeric plus ., -, _
    private static final String PACKAGE_NAME = "[a-zA-Z0-9]"
            + "(?:[a-zA-Z0-9._-]*[a-zA-Z0-9])?";

    // Optional extras: [extra1,extra2]
    private static final String EXTRAS = "(?:\\[[a-zA-Z0-9][a-zA-Z0-9,._-]*\\])?";

    // Specifier operator
    private static final String OP = "(?:~=|==|!=|<=|>=|<|>|===)";

    // Single specifier: operator + version
    private static final String SPECIFIER = OP + "\\s*" + EPOCH + RELEASE
            + PRE_RELEASE + POST_RELEASE + DEV_RELEASE + LOCAL;

    // Full package spec pattern
    private static final Pattern PACKAGE_SPEC_PATTERN = Pattern.compile(
            "^(" + PACKAGE_NAME + ")" + EXTRAS
                    + "(?:\\s*(" + SPECIFIER + "(?:\\s*,\\s*" + SPECIFIER + ")*))?$");

    private Pep440() {
    }

    /**
     * Validates that a version string conforms to PEP 440.
     */
    public static boolean isValidVersion(String version) {
        return version != null && VERSION_PATTERN.matcher(version).matches();
    }

    /**
     * Validates a package specification string (e.g., "numpy>=1.26.0,&lt;2.0.0").
     *
     * @return list of validation error messages, empty if valid
     */
    public static List<String> validatePackageSpec(String spec) {
        List<String> errors = new ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) {
            errors.add("Package spec is empty");
            return errors;
        }

        Matcher m = PACKAGE_SPEC_PATTERN.matcher(spec.trim());
        if (!m.matches()) {
            // Try to give a more specific error
            if (spec.contains(">") || spec.contains("<") || spec.contains("=")
                    || spec.contains("~") || spec.contains("!")) {
                // Has specifier operators, check if versions are valid
                String[] parts = spec.split("\\s*[><=!~]+\\s*", 2);
                if (parts.length > 0) {
                    String name = parts[0];
                    if (name.isEmpty() || !name.matches(PACKAGE_NAME)) {
                        errors.add("Invalid package name: '" + name + "'");
                    }
                }
                errors.add("Invalid version specifier format: '" + spec + "'");
            } else {
                // Plain package name - validate name only
                if (!spec.trim().matches(PACKAGE_NAME)) {
                    errors.add("Invalid package name format: '" + spec.trim()
                            + "'. Package names may contain letters, digits, '.', '-', and '_'.");
                }
            }
        }
        return errors;
    }

    /**
     * Validates a list of package specifications.
     *
     * @return list of error messages, one per invalid spec, empty if all valid
     */
    public static List<String> validatePackageSpecs(List<String> specs) {
        List<String> errors = new ArrayList<>();
        for (String spec : specs) {
            errors.addAll(validatePackageSpec(spec));
        }
        return errors;
    }
}
