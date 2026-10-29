package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LimiterKeysTest {

    @Test
    void hashed_isStableAndHasFixedLength() {
        assertThat(LimiterKeys.hashed("jano@example.com")).isEqualTo(LimiterKeys.hashed("jano@example.com"));
        assertThat(LimiterKeys.hashed("jano@example.com")).hasSize(64);
    }

    @Test
    void hashed_doesNotContainTheOriginalValue() {
        assertThat(LimiterKeys.hashed("jano@example.com")).doesNotContain("jano").doesNotContain("example");
    }

    @Test
    void hashed_differsForDifferentValues() {
        assertThat(LimiterKeys.hashed("a@example.com")).isNotEqualTo(LimiterKeys.hashed("b@example.com"));
    }
}