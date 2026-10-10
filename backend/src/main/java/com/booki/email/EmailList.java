package com.booki.email;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One or more report-recipient addresses, separated by {@code ;} — a reader
 * may want a report to reach more than one person (e.g. a parent and a
 * tutor). Used for {@code SendReportRequest.email}, {@code
 * GenerateSummaryRequest.email} and {@code User.defaultReportEmail} alike.
 */
public final class EmailList {

    private static final String SINGLE = "[^\\s@;]+@[^\\s@;]+\\.[^\\s@;]+";

    /** Compile-time constant, usable directly in {@code @Pattern(regexp = EmailList.REGEX)}. */
    public static final String REGEX = "^\\s*" + SINGLE + "\\s*(;\\s*" + SINGLE + "\\s*)*$";

    private static final Pattern VALID = Pattern.compile(REGEX);

    private EmailList() {
    }

    public static boolean isValid(String raw) {
        return raw != null && VALID.matcher(raw).matches();
    }

    public static List<String> parse(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    /** Trimmed, consistently "; "-joined form for storage and display. */
    public static String normalize(String raw) {
        return String.join("; ", parse(raw));
    }
}
