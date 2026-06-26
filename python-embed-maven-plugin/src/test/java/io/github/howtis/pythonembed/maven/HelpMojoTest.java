package io.github.howtis.pythonembed.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelpMojoTest {

    @Test
    void shouldDisplayBasicHelp() throws MojoExecutionException {
        HelpMojo mojo = new HelpMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        mojo.execute();

        assertTrue(log.containsInfo("python-embed-maven-plugin"),
                "Help should contain plugin name");
        assertTrue(log.containsInfo("setup"),
                "Help should mention setup goal");
        assertTrue(log.containsInfo("properties"),
                "Help should mention properties goal");
        assertTrue(log.containsInfo("help"),
                "Help should mention help goal");
    }

    @Test
    void shouldDisplayDetailForSetupGoal() throws Exception {
        HelpMojo mojo = new HelpMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        var detailField = HelpMojo.class.getDeclaredField("detail");
        detailField.setAccessible(true);
        detailField.setBoolean(mojo, true);

        var goalField = HelpMojo.class.getDeclaredField("goal");
        goalField.setAccessible(true);
        goalField.set(mojo, "setup");

        mojo.execute();

        assertTrue(log.containsInfo("packages"),
                "Detail should list packages parameter");
        assertTrue(log.containsInfo("pythonVersion"),
                "Detail should list pythonVersion parameter");
    }

    @Test
    void shouldDisplayDetailForPropertiesGoal() throws Exception {
        HelpMojo mojo = new HelpMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        var detailField = HelpMojo.class.getDeclaredField("detail");
        detailField.setAccessible(true);
        detailField.setBoolean(mojo, true);

        var goalField = HelpMojo.class.getDeclaredField("goal");
        goalField.setAccessible(true);
        goalField.set(mojo, "properties");

        mojo.execute();

        assertTrue(log.containsInfo("skip"),
                "Detail should list skip parameter");
    }

    @Test
    void shouldHandleUnknownGoal() throws Exception {
        HelpMojo mojo = new HelpMojo();
        TestLog log = new TestLog();
        mojo.setLog(log);

        var detailField = HelpMojo.class.getDeclaredField("detail");
        detailField.setAccessible(true);
        detailField.setBoolean(mojo, true);

        var goalField = HelpMojo.class.getDeclaredField("goal");
        goalField.setAccessible(true);
        goalField.set(mojo, "nonexistent");

        mojo.execute();

        assertTrue(log.containsInfo("Goal: nonexistent"),
                "Should still show goal header even for unknown goal");
    }

}
