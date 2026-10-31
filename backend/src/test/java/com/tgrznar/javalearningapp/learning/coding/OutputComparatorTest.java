package com.tgrznar.javalearningapp.learning.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    @Test
    void identicalTexts_match() {
        assertThat(OutputComparator.matches("Hello\nWorld\n", "Hello\nWorld\n")).isTrue();
    }

    @Test
    void windowsLineEndings_areTheSameAsUnixOnes() {
        assertThat(OutputComparator.matches("a\nb\n", "a\r\nb\r\n")).isTrue();
        assertThat(OutputComparator.matches("a\r\nb\r\n", "a\nb\n")).isTrue();
    }

    @Test
    void trailingSpacesOnALine_areIgnored() {
        assertThat(OutputComparator.matches("a\nb", "a   \nb  ")).isTrue();
    }

    @Test
    void trailingNewlinesAtTheEnd_areIgnored() {
        assertThat(OutputComparator.matches("a\nb", "a\nb\n\n\n")).isTrue();
        assertThat(OutputComparator.matches("a\nb\n\n", "a\nb")).isTrue();
    }

    @Test
    void trailingBlankLinesWithSpaces_areIgnored() {
        assertThat(OutputComparator.matches("a", "a\n   \n  \n")).isTrue();
    }

    @Test
    void emptyOutputs_matchEachOtherAndAnOnlyNewline() {
        assertThat(OutputComparator.matches("", "")).isTrue();
        assertThat(OutputComparator.matches("", "\n")).isTrue();
        assertThat(OutputComparator.matches("\n", "")).isTrue();
    }

    @Test
    void leadingSpacesOnALine_matter() {
        assertThat(OutputComparator.matches("a", " a")).isFalse();
    }

    @Test
    void spacesInsideALine_matter() {
        assertThat(OutputComparator.matches("a b", "a  b")).isFalse();
    }

    @Test
    void caseMatters() {
        assertThat(OutputComparator.matches("Hello", "hello")).isFalse();
    }

    @Test
    void blankLineInTheMiddle_matters() {
        assertThat(OutputComparator.matches("a\nb", "a\n\nb")).isFalse();
    }

    @Test
    void leadingBlankLine_matters() {
        assertThat(OutputComparator.matches("a", "\na")).isFalse();
    }

    @Test
    void extraOrMissingLine_doesNotMatch() {
        assertThat(OutputComparator.matches("a\nb", "a\nb\nc")).isFalse();
        assertThat(OutputComparator.matches("a\nb", "a")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0.1,0.10", "3.0,3", "1e3,1000", "0.30000000000000004,0.3"})
    void numbers_areComparedCharacterByCharacter(String expected, String actual) {
        assertThat(OutputComparator.matches(expected, actual)).isFalse();
    }

    @Test
    void tabs_areNotTrimmed() {
        assertThat(OutputComparator.matches("a", "a\t")).isFalse();
    }

    @Test
    void missingTexts_neverMatch() {
        assertThat(OutputComparator.matches("a", null)).isFalse();
        assertThat(OutputComparator.matches(null, "a")).isFalse();
        assertThat(OutputComparator.matches(null, null)).isFalse();
    }
}
