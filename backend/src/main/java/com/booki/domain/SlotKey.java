package com.booki.domain;

/**
 * The named prompts ("SlotPrompts") that make up an AI Profile. Label, group and
 * the locked frame live here — only the editable text and its original are
 * stored per profile. The wire key used by the API is {@code name().toLowerCase()}.
 */
public enum SlotKey {

    PERSONA("Persona", Group.PERSONA, null, null),
    READER_CONTEXT("Reader context", Group.READER, null, null),

    RUBRIC_EASY("Difficulty — Easy", Group.DIFFICULTY, null, null),
    RUBRIC_MEDIUM("Difficulty — Medium", Group.DIFFICULTY, null, null),
    RUBRIC_HARD("Difficulty — Advanced", Group.DIFFICULTY, null, null),

    FN_QUIZ_QUESTION("Function — Quiz question", Group.FUNCTIONS,
            "Output only the question. No preamble, no numbering, no quotes.", null),
    FN_ANSWER_GRADING("Function — Answer grading", Group.FUNCTIONS,
            "Reply in exactly three lines and nothing else:\nCORRECT: yes or no\n"
                    + "SCORE: a number from 0.0 to 1.0\nFEEDBACK: one short sentence", null),
    FN_SUMMARY("Function — Summary", Group.FUNCTIONS,
            "Write prose only. No headings unless the reader asks for them.", null),
    FN_EXPLAIN("Function — Explain", Group.FUNCTIONS, null, null),
    FN_MNEMONIC("Function — Mnemonic", Group.FUNCTIONS, null, null),

    CAPABILITY_ROUTING("Capability routing", Group.ROUTING,
            "If a specialized capability fits the reader's last message better than a prose reply, "
                    + "respond with only {\"capability\":\"<name>\"}. Otherwise answer normally.", null);

    public enum Group {
        PERSONA, READER, DIFFICULTY, FUNCTIONS, ROUTING;

        public String wire() {
            return name().toLowerCase();
        }
    }

    private final String label;
    private final Group group;
    private final String lockedPreamble;
    private final String lockedPostamble;

    SlotKey(String label, Group group, String lockedPreamble, String lockedPostamble) {
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

    public static SlotKey ofWire(String wire) {
        return valueOf(wire.toUpperCase());
    }
}
