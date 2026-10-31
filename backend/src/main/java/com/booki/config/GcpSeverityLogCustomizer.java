package com.booki.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Adds the {@code severity} field Cloud Logging reads from JSON log lines.
 * Without it every Cloud Run stdout entry has no severity, so a
 * {@code severity>=ERROR} filter finds nothing. Only used when structured
 * logging is on (deploy.yml sets it; local runs stay plain text).
 */
public class GcpSeverityLogCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("severity", (ILoggingEvent event) -> severityOf(event.getLevel()));
    }

    static String severityOf(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return "ERROR";
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
            return "WARNING";
        }
        return level.isGreaterOrEqual(Level.INFO) ? "INFO" : "DEBUG";
    }
}
