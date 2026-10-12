package com.tgrznar.javalearningapp.schoolclass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JoinCodeGeneratorTest {

    @Mock
    private SchoolClassRepository classRepository;

    private JoinCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new JoinCodeGenerator(classRepository);
    }

    @Test
    void generate_hasExpectedLengthAndOnlyAlphabetCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate())
                    .hasSize(JoinCodeGenerator.CODE_LENGTH)
                    .matches("[" + JoinCodeGenerator.ALPHABET + "]+");
        }
    }

    @Test
    void generate_neverContainsCharactersThatLookAlike() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void generate_producesDifferentCodes() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void generateUnique_unusedCode_isReturnedImmediately() {
        when(classRepository.existsByJoinCode(anyString())).thenReturn(false);

        String code = generator.generateUnique();

        assertThat(code).hasSize(JoinCodeGenerator.CODE_LENGTH);
        verify(classRepository, times(1)).existsByJoinCode(code);
    }

    @Test
    void generateUnique_collision_triesAgain() {
        when(classRepository.existsByJoinCode(anyString())).thenReturn(true, true, false);

        String code = generator.generateUnique();

        assertThat(code).hasSize(JoinCodeGenerator.CODE_LENGTH);
        verify(classRepository, times(3)).existsByJoinCode(anyString());
    }

    @Test
    void generateUnique_allAttemptsCollide_throws() {
        when(classRepository.existsByJoinCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.generateUnique())
                .isInstanceOf(IllegalStateException.class);
        verify(classRepository, times(JoinCodeGenerator.MAX_ATTEMPTS)).existsByJoinCode(anyString());
    }
}