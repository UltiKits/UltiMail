package com.ultikits.plugins.mail.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UltiKits/UltiMail#37: a value inserted for one placeholder is never expanded again by a later
 * placeholder of the same line.
 */
@DisplayName("Placeholders (UltiKits/UltiMail#37)")
class PlaceholdersTest {

    @Test
    @DisplayName("POSITIVE CONTROL: every placeholder of the line is filled")
    void fillsEveryPlaceholder() {
        assertThat(Placeholders.fill("{A} and {B}", "{A}", "one", "{B}", "two"))
                .isEqualTo("one and two");
    }

    @Test
    @DisplayName("A value containing a later placeholder's token is inserted as written")
    void aValueContainingALaterTokenStaysLiteral() {
        assertThat(Placeholders.fill("{A} and {B}", "{A}", "x{B}", "{B}", "two"))
                .isEqualTo("x{B} and two");
    }

    @Test
    @DisplayName("A value containing an earlier placeholder's token is inserted as written")
    void aValueContainingAnEarlierTokenStaysLiteral() {
        assertThat(Placeholders.fill("{A} and {B}", "{A}", "one", "{B}", "y{A}"))
                .isEqualTo("one and y{A}");
    }

    @Test
    @DisplayName("A token with no value is left as written, and a null value is written as null")
    void unknownTokensAndNullValues() {
        assertThat(Placeholders.fill("{A} {C}", "{A}", null)).isEqualTo("null {C}");
    }
}
