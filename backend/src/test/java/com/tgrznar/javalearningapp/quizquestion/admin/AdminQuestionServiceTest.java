package com.tgrznar.javalearningapp.quizquestion.admin;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.quizquestion.dto.QuestionRequest;
import com.tgrznar.javalearningapp.quizquestion.dto.QuestionResponse;
import com.tgrznar.javalearningapp.quizquestion.exception.QuestionNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long MODULE_ID = 1L;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private CourseModuleRepository moduleRepository;

    @InjectMocks
    private AdminQuestionService service;

    // ---------- create ----------

    @Test
    void create_trimsTextAndStoresTheCorrectOption() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module(MODULE_ID, true)));
        when(questionRepository.saveAndFlush(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(MODULE_ID, new QuestionRequest("  Co je JVM?  ", " A ", " B ", " C ", " D ", QuizOption.C), ADMIN_ID);

        ArgumentCaptor<QuizQuestion> captor = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(questionRepository).saveAndFlush(captor.capture());
        QuizQuestion saved = captor.getValue();
        assertThat(saved.getQuestionText()).isEqualTo("Co je JVM?");
        assertThat(saved.getOptionA()).isEqualTo("A");
        assertThat(saved.getOptionB()).isEqualTo("B");
        assertThat(saved.getOptionC()).isEqualTo("C");
        assertThat(saved.getOptionD()).isEqualTo("D");
        assertThat(saved.getCorrectOption()).isEqualTo(QuizOption.C);
        assertThat(saved.getModule().getId()).isEqualTo(MODULE_ID);
    }

    @Test
    void create_inactiveModule_isAllowed() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module(MODULE_ID, false)));
        when(questionRepository.saveAndFlush(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(MODULE_ID, request(QuizOption.A), ADMIN_ID);

        verify(questionRepository).saveAndFlush(any(QuizQuestion.class));
    }

    @Test
    void create_returnsTheSavedQuestion() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module(MODULE_ID, true)));
        when(questionRepository.saveAndFlush(any(QuizQuestion.class))).thenAnswer(inv -> {
            QuizQuestion q = inv.getArgument(0);
            q.setId(7L);
            return q;
        });

        QuestionResponse response = service.create(MODULE_ID, request(QuizOption.D), ADMIN_ID);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.moduleId()).isEqualTo(MODULE_ID);
        assertThat(response.correctOption()).isEqualTo(QuizOption.D);
    }

    @Test
    void create_unknownModule_throwsNotFoundAndSavesNothing() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(MODULE_ID, request(QuizOption.A), ADMIN_ID))
                .isInstanceOf(ModuleNotFoundException.class);

        verifyNoInteractions(questionRepository);
    }

    // ---------- list ----------

    @Test
    void list_returnsQuestionsInRepositoryOrder() {
        CourseModule module = module(MODULE_ID, true);
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID))
                .thenReturn(List.of(question(1L, module), question(2L, module)));

        List<QuestionResponse> result = service.list(MODULE_ID);

        assertThat(result).extracting(QuestionResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void list_unknownModule_throwsNotFound() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.list(MODULE_ID)).isInstanceOf(ModuleNotFoundException.class);

        verifyNoInteractions(questionRepository);
    }

    // ---------- get ----------

    @Test
    void get_returnsQuestionWithCorrectOption() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID))
                .thenReturn(Optional.of(question(5L, module(MODULE_ID, true))));

        QuestionResponse response = service.get(MODULE_ID, 5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.correctOption()).isEqualTo(QuizOption.B);
    }

    @Test
    void get_unknownModule_throwsModuleNotFound() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.get(MODULE_ID, 5L)).isInstanceOf(ModuleNotFoundException.class);
    }

    @Test
    void get_questionOfAnotherModule_throwsQuestionNotFound() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MODULE_ID, 5L)).isInstanceOf(QuestionNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFields() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID))
                .thenReturn(Optional.of(question(5L, module(MODULE_ID, true))));
        when(questionRepository.saveAndFlush(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = service.update(MODULE_ID, 5L,
                new QuestionRequest(" New text ", "New A", "New B", "New C", "New D", QuizOption.D), ADMIN_ID);

        assertThat(response.questionText()).isEqualTo("New text");
        assertThat(response.optionA()).isEqualTo("New A");
        assertThat(response.optionD()).isEqualTo("New D");
        assertThat(response.correctOption()).isEqualTo(QuizOption.D);
    }

    @Test
    void update_unknownQuestion_throwsNotFoundAndSavesNothing() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(MODULE_ID, 5L, request(QuizOption.A), ADMIN_ID))
                .isInstanceOf(QuestionNotFoundException.class);

        verify(questionRepository, never()).saveAndFlush(any());
    }

    // ---------- delete ----------

    @Test
    void delete_removesTheQuestion() {
        QuizQuestion question = question(5L, module(MODULE_ID, true));
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID)).thenReturn(Optional.of(question));

        service.delete(MODULE_ID, 5L, ADMIN_ID);

        verify(questionRepository).delete(question);
    }

    @Test
    void delete_unknownQuestion_throwsNotFoundAndDeletesNothing() {
        when(moduleRepository.existsById(MODULE_ID)).thenReturn(true);
        when(questionRepository.findByIdAndModuleId(5L, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(MODULE_ID, 5L, ADMIN_ID))
                .isInstanceOf(QuestionNotFoundException.class);

        verify(questionRepository, never()).delete(any());
    }

    // ---------- helpers ----------

    private static QuestionRequest request(QuizOption correct) {
        return new QuestionRequest("Question", "A", "B", "C", "D", correct);
    }

    private static CourseModule module(Long id, boolean active) {
        CourseModule m = new CourseModule();
        m.setId(id);
        m.setTitle("Module " + id);
        m.setOrderNumber(id.intValue());
        m.setActive(active);
        return m;
    }

    private static QuizQuestion question(Long id, CourseModule module) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setModule(module);
        q.setQuestionText("Question " + id);
        q.setOptionA("A");
        q.setOptionB("B");
        q.setOptionC("C");
        q.setOptionD("D");
        q.setCorrectOption(QuizOption.B);
        return q;
    }
}