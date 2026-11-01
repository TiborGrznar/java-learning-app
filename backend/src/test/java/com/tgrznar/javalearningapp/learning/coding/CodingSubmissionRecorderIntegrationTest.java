package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Storing a submission and deriving the codingCompleted flag, against a real database. The rule: the
 * coding part is completed when the student has a PASSED result for every active task of the module.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CodingSubmissionRecorderIntegrationTest {

    @Autowired
    private CodingSubmissionRecorder recorder;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingResultRepository resultRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    private static AttemptEvaluation passed() {
        return new AttemptEvaluation(CodingResultStatus.PASSED, 2, 2, null, null, List.of());
    }

    private static AttemptEvaluation failed() {
        return new AttemptEvaluation(CodingResultStatus.FAILED, 1, 2, null, "wrong output", List.of());
    }

    @Test
    void passedSubmission_storesTheResultAndCompletesAModuleWithOneTask() {
        CourseModule module = saveModule();
        CodingTask task = saveTask(module, 1, true);
        User student = studentWithProgress(module);

        boolean completed = recorder.record(student.getId(), module.getId(), task.getId(), "class Solution {}", passed());

        assertThat(completed).isTrue();
        assertThat(progress(student, module).isCodingCompleted()).isTrue();
        List<CodingResult> results = resultRepository.findByUserIdAndCodingTaskId(student.getId(), task.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(CodingResultStatus.PASSED);
        assertThat(results.get(0).getSubmittedCode()).isEqualTo("class Solution {}");
        assertThat(results.get(0).getPassedCases()).isEqualTo(2);
        assertThat(results.get(0).getTotalCases()).isEqualTo(2);
    }

    @Test
    void failedSubmission_isStoredButDoesNotCompleteTheModule() {
        CourseModule module = saveModule();
        CodingTask task = saveTask(module, 1, true);
        User student = studentWithProgress(module);

        boolean completed = recorder.record(student.getId(), module.getId(), task.getId(), "code", failed());

        assertThat(completed).isFalse();
        assertThat(progress(student, module).isCodingCompleted()).isFalse();
        CodingResult result = resultRepository.findByUserIdAndCodingTaskId(student.getId(), task.getId()).get(0);
        assertThat(result.getStatus()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(result.getActualOutput()).isEqualTo("wrong output");
        assertThat(result.getPassedCases()).isEqualTo(1);
    }

    @Test
    void moduleWithTwoTasks_isCompletedOnlyWhenBothArePassed() {
        CourseModule module = saveModule();
        CodingTask first = saveTask(module, 1, true);
        CodingTask second = saveTask(module, 2, true);
        User student = studentWithProgress(module);

        assertThat(recorder.record(student.getId(), module.getId(), first.getId(), "a", passed())).isFalse();
        assertThat(progress(student, module).isCodingCompleted()).isFalse();

        assertThat(recorder.record(student.getId(), module.getId(), second.getId(), "b", passed())).isTrue();
        assertThat(progress(student, module).isCodingCompleted()).isTrue();
    }

    @Test
    void passingTheSameTaskTwice_doesNotCompleteAModuleWithAnotherTask() {
        CourseModule module = saveModule();
        CodingTask first = saveTask(module, 1, true);
        saveTask(module, 2, true);
        User student = studentWithProgress(module);

        recorder.record(student.getId(), module.getId(), first.getId(), "a", passed());
        boolean completed = recorder.record(student.getId(), module.getId(), first.getId(), "a again", passed());

        assertThat(completed).isFalse();
    }

    @Test
    void inactiveTasks_doNotCount() {
        CourseModule module = saveModule();
        CodingTask active = saveTask(module, 1, true);
        saveTask(module, 2, false);
        User student = studentWithProgress(module);

        boolean completed = recorder.record(student.getId(), module.getId(), active.getId(), "a", passed());

        assertThat(completed).isTrue();
    }

    @Test
    void flagStaysSet_whenALaterSubmissionFails() {
        CourseModule module = saveModule();
        CodingTask task = saveTask(module, 1, true);
        User student = studentWithProgress(module);
        recorder.record(student.getId(), module.getId(), task.getId(), "good", passed());

        boolean completed = recorder.record(student.getId(), module.getId(), task.getId(), "worse", failed());

        assertThat(completed).isTrue();
        assertThat(progress(student, module).isCodingCompleted()).isTrue();
    }

    @Test
    void flagStaysSet_whenATaskIsAddedLater() {
        CourseModule module = saveModule();
        CodingTask task = saveTask(module, 1, true);
        User student = studentWithProgress(module);
        recorder.record(student.getId(), module.getId(), task.getId(), "good", passed());

        saveTask(module, 2, true);
        boolean completed = recorder.record(student.getId(), module.getId(), task.getId(), "again", passed());

        assertThat(completed).isTrue();
    }

    @Test
    void resultsOfOtherStudents_doNotCountForThisStudent() {
        CourseModule module = saveModule();
        CodingTask first = saveTask(module, 1, true);
        CodingTask second = saveTask(module, 2, true);
        User student = studentWithProgress(module);
        User other = studentWithProgress(module);
        recorder.record(other.getId(), module.getId(), second.getId(), "x", passed());

        boolean completed = recorder.record(student.getId(), module.getId(), first.getId(), "a", passed());

        assertThat(completed).isFalse();
    }

    @Test
    void missingProgressRow_isAServerFaultAndStoresNothing() {
        CourseModule module = saveModule();
        CodingTask task = saveTask(module, 1, true);
        User student = enrolledClasses.saveEnrolledStudent();

        assertThatThrownBy(() -> recorder.record(student.getId(), module.getId(), task.getId(), "code", passed()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(resultRepository.findByUserIdAndCodingTaskId(student.getId(), task.getId())).isEmpty();
    }

    // ---------- helpers ----------

    private UserProgress progress(User student, CourseModule module) {
        return progressRepository.findByUserIdAndModuleId(student.getId(), module.getId()).orElseThrow();
    }

    private User studentWithProgress(CourseModule module) {
        User student = enrolledClasses.saveEnrolledStudent();
        UserProgress progress = new UserProgress();
        progress.setUser(student);
        progress.setModule(module);
        progress.setTheoryCompleted(true);
        progressRepository.saveAndFlush(progress);
        return student;
    }

    private CourseModule saveModule() {
        CourseModule module = new CourseModule();
        module.setTitle("Module");
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(1);
        module.setActive(true);
        return moduleRepository.saveAndFlush(module);
    }

    private CodingTask saveTask(CourseModule module, int order, boolean active) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setOrderNumber(order);
        task.setTitle("Task " + order);
        task.setActive(active);
        return taskRepository.saveAndFlush(task);
    }
}
