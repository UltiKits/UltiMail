package com.ultikits.plugins.mail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StartupCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
        "1.19.2-R0.1-SNAPSHOT,6.3.0,REFUSE",
        "1.20.6-R0.1-SNAPSHOT,6.3.1-SNAPSHOT,REFUSE",
        "1.20.6,6.4.0,ALLOW",
        "1.20.6,6.4.0-SNAPSHOT,ALLOW",
        "1.21,6.3.0,ALLOW",
        "1.21.11,6.3.0,ALLOW",
        "1.20.6,6.4.1,ALLOW",
        "1.20.6,6.10.0,ALLOW",
        "1.20.6,7.0.0,ALLOW",
        "2.0,6.3.0,ALLOW",
        "1.20.6,6.3.0-SNAPSHOT,REFUSE",
        "1.20.6,6.4,ALLOW",
        "1.19.2,5.9.9,REFUSE",
        "0.9,6.3.0,REFUSE"
    })
    void versionMatrix(String minecraft, String framework, StartupCompatibility.Decision expected) {
        assertThat(StartupCompatibility.evaluate(minecraft, framework)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"garbage", "1", "1.x.2", "1.20.6oops", "-1.20.6", "1.20.6.1", "999999999999.1"})
    void invalidMinecraftIsUnknown(String version) {
        assertThat(StartupCompatibility.evaluate(version, "6.3.0"))
                .isEqualTo(StartupCompatibility.Decision.UNKNOWN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"garbage", "6", "6.x.0", "6.4oops", "6.4.0.1", "999999999999.1", "6.4.999999999999"})
    void invalidFrameworkIsUnknownEvenOnNewMinecraft(String version) {
        assertThat(StartupCompatibility.evaluate("1.20.6", version))
                .isEqualTo(StartupCompatibility.Decision.UNKNOWN);
        assertThat(StartupCompatibility.evaluate("1.21.11", version))
                .isEqualTo(StartupCompatibility.Decision.UNKNOWN);
    }
}
