package com.booki.domain;

/**
 * The account-wide "App Profile" slots: what each difficulty level means
 * (quiz questions and grading only), how BooKI's functions behave (quiz
 * question, answer grading, summary, explain, mnemonic), and when it routes
 * to one on its own in chat. One editable set per account — never per tutor
 * profile. The wire key used by the API is {@code name().toLowerCase()}.
 */
public enum AppProfileSlotKey {

    EASY("Difficulty — Easy", Group.DIFFICULTY, null, null),
    MEDIUM("Difficulty — Medium", Group.DIFFICULTY, null, null),
    HARD("Difficulty — Advanced", Group.DIFFICULTY, null, null),

    FN_QUIZ_QUESTION("Function — Quiz question", Group.FUNCTIONS,
            "Output only the question — one open question the reader answers in their own words. "
                    + "No preamble, no surrounding quotes, no number or label before it, and no "
                    + "multiple-choice options.", null),
    FN_ANSWER_GRADING("Function — Answer grading", Group.FUNCTIONS,
            "Reply in exactly this format and nothing else:\n"
                    + "SCORE: a number from 0.0 to 1.0 for how complete and accurate the answer is\n"
                    + "FEEDBACK: two to four warm sentences — first what the reader got right, then the "
                    + "part they missed or misread together with the correct information from the page. "
                    + "Never tell them to try again.", null),
    FN_SUMMARY("Function — Summary", Group.FUNCTIONS,
            "Write prose only. No headings unless the reader asks for them.", null),
    FN_EXPLAIN("Function — Explain", Group.FUNCTIONS, null, null),
    FN_MNEMONIC("Function — Mnemonic", Group.FUNCTIONS, null, null),

    CAPABILITY_ROUTING("Capability routing", Group.ROUTING,
            "If a specialized capability fits the reader's last message better than a prose reply, "
                    + "respond with only {\"capability\":\"<name>\"}. Otherwise answer normally.", null);

    public enum Group {
        DIFFICULTY, FUNCTIONS, ROUTING;

        public String wire() {
            return name().toLowerCase();
        }
    }

    private final String label;
    private final Group group;
    private final String lockedPreamble;
    private final String lockedPostamble;

    AppProfileSlotKey(String label, Group group, String lockedPreamble, String lockedPostamble) {
        this.label = label;
        this.group = group;
        this.lockedPreamble = lockedPreamble;
        this.lockedPostamble = lockedPostamble;
    }

    public String wire() {
        return name().toLowerCase();
    }

    public String label() {
        return label;
    }

    public Group group() {
        return group;
    }

    public String lockedPreamble() {
        return lockedPreamble;
    }

    public String lockedPostamble() {
        return lockedPostamble;
    }

    public static AppProfileSlotKey ofWire(String wire) {
        return valueOf(wire.toUpperCase());
    }
}
