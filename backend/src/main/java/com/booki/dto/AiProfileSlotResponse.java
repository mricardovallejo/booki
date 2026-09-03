package com.booki.dto;

/**
 * One SlotPrompt of an AI Profile. {@code text} is editable; the locked frame
 * ({@code lockedPreamble}/{@code lockedPostamble}, either may be null) is fixed.
 * {@code originalText} is the seeded baseline for the Edited/Original badge.
 */
public record AiProfileSlotResponse(
        String key,
        String label,
        String group,
        String lockedPreamble,
        String lockedPostamble,
        String text,
        String originalText,
        boolean modified) {
}
