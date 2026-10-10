package com.booki.dto;

/**
 * One row of the account-wide App Profile (a difficulty rubric, function
 * prompt, or the capability-routing instruction). {@code text} is editable;
 * {@code originalText} is the shipped baseline for the Edited/Original badge.
 */
public record AppProfileSlotResponse(
        String key,
        String label,
        String group,
        String lockedPreamble,
        String lockedPostamble,
        String text,
        String originalText,
        boolean modified) {
}
