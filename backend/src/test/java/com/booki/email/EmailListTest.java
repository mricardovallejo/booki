package com.booki.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailListTest {

    @Test
    void singleAddressIsValid() {
        assertThat(EmailList.isValid("reader@example.com")).isTrue();
        assertThat(EmailList.parse("reader@example.com")).containsExactly("reader@example.com");
    }

    @Test
    void semicolonSeparatedListIsValidAndParsesEachAddress() {
        assertThat(EmailList.isValid("parent@example.com; tutor@example.com")).isTrue();
        assertThat(EmailList.parse("parent@example.com; tutor@example.com"))
                .containsExactly("parent@example.com", "tutor@example.com");
    }

    @Test
    void normalizeTrimsAndCollapsesSpacingConsistently() {
        assertThat(EmailList.normalize(" parent@example.com ;tutor@example.com  "))
                .isEqualTo("parent@example.com; tutor@example.com");
    }

    @Test
    void blankOrMalformedInputIsInvalid() {
        assertThat(EmailList.isValid(null)).isFalse();
        assertThat(EmailList.isValid("")).isFalse();
        assertThat(EmailList.isValid("   ")).isFalse();
        assertThat(EmailList.isValid("not-an-email")).isFalse();
        assertThat(EmailList.isValid("parent@example.com;;tutor@example.com")).isFalse();
        assertThat(EmailList.isValid("parent@example.com,tutor@example.com")).isFalse();
    }
}
