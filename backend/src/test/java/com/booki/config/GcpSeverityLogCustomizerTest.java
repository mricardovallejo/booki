package com.booki.config;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GcpSeverityLogCustomizerTest {

    @Test
    void mapsLogbackLevelsToCloudLoggingSeverities() {
        assertThat(GcpSeverityLogCustomizer.severityOf(Level.ERROR)).isEqualTo("ERROR");
        assertThat(GcpSeverityLogCustomizer.severityOf(Level.WARN)).isEqualTo("WARNING");
        assertThat(GcpSeverityLogCustomizer.severityOf(Level.INFO)).isEqualTo("INFO");
        assertThat(GcpSeverityLogCustomizer.severityOf(Level.DEBUG)).isEqualTo("DEBUG");
    }
}
