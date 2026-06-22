package io.github.howtis.pythonembed.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Pep440Test {

    @Test
    void isValidVersion_simpleRelease_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1.0.0"));
        assertTrue(Pep440.isValidVersion("0.0.1"));
        assertTrue(Pep440.isValidVersion("2024.1.0"));
        assertTrue(Pep440.isValidVersion("3.12"));
    }

    @Test
    void isValidVersion_withEpoch_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1!1.0.0"));
    }

    @Test
    void isValidVersion_withPreRelease_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1.0.0a1"));
        assertTrue(Pep440.isValidVersion("1.0.0b2"));
        assertTrue(Pep440.isValidVersion("1.0.0rc3"));
    }

    @Test
    void isValidVersion_withPostRelease_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1.0.0.post1"));
    }

    @Test
    void isValidVersion_withDevRelease_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1.0.0.dev0"));
    }

    @Test
    void isValidVersion_withLocal_returnsTrue() {
        assertTrue(Pep440.isValidVersion("1.0.0+local"));
        assertTrue(Pep440.isValidVersion("1.0.0+ubuntu.20.04"));
    }

    @Test
    void isValidVersion_invalid_returnsFalse() {
        assertFalse(Pep440.isValidVersion(""));
        assertFalse(Pep440.isValidVersion("not.a.version"));
        assertFalse(Pep440.isValidVersion("1.0.0-rc1"));
    }

    @Test
    void validatePackageSpec_plainName_emptyErrors() {
        assertTrue(Pep440.validatePackageSpec("numpy").isEmpty());
        assertTrue(Pep440.validatePackageSpec("python-dateutil").isEmpty());
        assertTrue(Pep440.validatePackageSpec("my_package").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "numpy==1.26.4",
            "torch>=2.0.0",
            "requests~=2.28",
            "package!=1.0.0",
            "pkg<=2.0,>=1.0",
            "numpy>=1.26.0,<2.0.0",
            "pkg===1.0.0",
            "tensorflow[and-cuda]>=2.16.0",
            "mypkg>=1.0.0a1",
            "mypkg>=1.0.0.post1",
            "mypkg>=1.0.0.dev0",
    })
    void validatePackageSpec_validSpecs_emptyErrors(String spec) {
        List<String> errors = Pep440.validatePackageSpec(spec);
        assertTrue(errors.isEmpty(), "Expected valid: " + spec + " but got: " + errors);
    }

    @Test
    void validatePackageSpec_invalidVersionFormat_returnsErrors() {
        List<String> errors = Pep440.validatePackageSpec("numpy>=not.a.version");
        assertFalse(errors.isEmpty());
    }

    @Test
    void validatePackageSpec_emptyString_returnsErrors() {
        List<String> errors = Pep440.validatePackageSpec("");
        assertFalse(errors.isEmpty());
    }

    @Test
    void validatePackageSpecs_allValid_emptyErrors() {
        List<String> specs = List.of("numpy==1.26.4", "torch>=2.0.0", "requests~=2.28");
        assertTrue(Pep440.validatePackageSpecs(specs).isEmpty());
    }

    @Test
    void validatePackageSpecs_mixed_returnsOnlyInvalidErrors() {
        List<String> specs = List.of("numpy==1.26.4", "bad one", "torch>=2.0.0");
        List<String> errors = Pep440.validatePackageSpecs(specs);
        assertEquals(1, errors.size());
    }

    @Test
    void validatePackageSpec_null_returnsError() {
        List<String> errors = Pep440.validatePackageSpec(null);
        assertFalse(errors.isEmpty());
    }
}
