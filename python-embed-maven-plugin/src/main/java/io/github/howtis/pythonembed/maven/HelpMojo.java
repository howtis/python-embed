package io.github.howtis.pythonembed.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * Display help information on python-embed-maven-plugin.
 * Reads the plugin descriptor ({@code META-INF/maven/plugin.xml}) at runtime.
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

    private static final String DESCRIPTOR = "META-INF/maven/plugin.xml";

    @Override
    public void execute() throws MojoExecutionException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(DESCRIPTOR)) {
            if (in == null) {
                getLog().warn("Plugin descriptor not found: " + DESCRIPTOR);
                return;
            }
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(in);
            Element root = doc.getDocumentElement();

            StringBuilder sb = new StringBuilder();
            sb.append(text(root, "name")).append(' ')
                    .append(text(root, "version")).append('\n')
                    .append(text(root, "description")).append("\n\n");

            NodeList mojos = root.getElementsByTagName("mojo");
            if (detail) {
                boolean found = false;
                for (int i = 0; i < mojos.getLength(); i++) {
                    Element m = (Element) mojos.item(i);
                    String g = text(m, "goal");
                    if (goal != null && !goal.isEmpty() && !g.equals(goal)) {
                        continue;
                    }
                    found = true;
                    sb.append("Goal: ").append(g).append('\n')
                            .append(text(m, "description")).append("\n\n")
                            .append("Parameters:\n\n");
                    NodeList params = m.getElementsByTagName("parameter");
                    for (int j = 0; j < params.getLength(); j++) {
                        Element p = (Element) params.item(j);
                        if ("false".equals(text(p, "editable"))) {
                            continue;
                        }
                        String type = simpleName(text(p, "type"));
                        sb.append(indent(text(p, "name"),
                                type + " - " + text(p, "description")));
                    }
                }
                if (!found) {
                    sb.append("No goal: ").append(goal).append('\n');
                }
            } else {
                sb.append("Goals:\n\n");
                for (int i = 0; i < mojos.getLength(); i++) {
                    Element m = (Element) mojos.item(i);
                    sb.append(indent(text(m, "goal"),
                            text(m, "description")));
                }
                sb.append("Use -Ddetail=true -Dgoal=<goal> for parameters.\n");
            }

            getLog().info(sb.toString());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate help", e);
        }
    }

    private String indent(String name, String desc) {
        return " ".repeat(indentSize) + name + '\n'
                + " ".repeat(indentSize * 2) + desc + "\n\n";
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent() : "";
    }

    private static String simpleName(String type) {
        if (type == null || type.isEmpty()) {
            return type;
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }
}
