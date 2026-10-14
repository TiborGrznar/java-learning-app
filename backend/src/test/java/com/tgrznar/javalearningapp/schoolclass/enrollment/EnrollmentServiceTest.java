package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.EnrollmentResponse;
import com.tgrznar.javalearningapp.schoolclass.exception.AlreadyEnrolledException;
import com.tgrznar.javalearningapp.schoolclass.exception.InvalidJoinCodeException;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolMismatchException;
import com.tgrznar.javalearningapp.schoolclass.exception.TooManyAttemptsException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final Long STUDENT_ID = 10L;
    private static final String CODE = "ABCD2345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private EnrollmentAttemptLimiter attemptLimiter;

    @InjectMocks
    private EnrollmentService service;

    @Test
    void enroll_studentWithoutSchool_joinsClassAndTakesItsSchool() {
        User student = student(null);
        SchoolClass schoolClass = schoolClass(20L, school(5L));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(classRepository.findJoinableByJoinCode(CODE)).thenReturn(Optional.of(schoolClass));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enroll(STUDENT_ID, CODE);

        assertThat(response.classId()).isEqualTo(20L);
        assertThat(response.className()).isEqualTo("Class 20");
        assertThat(response.schoolId()).isEqualTo(5L);
        assertThat(student.getSchoolClass()).isSameAs(schoolClass);
        assertThat(student.getSchool().getId()).isEqualTo(5L);
        verify(attemptLimiter).recordSuccess(STUDENT_ID);
        verify(attemptLimiter, never()).recordFailure(any());
    }

    @Test
    void enroll_trimsAndUpperCasesTheCode() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student(null)));
        when(classRepository.findJoinableByJoinCode(CODE)).thenReturn(Optional.of(schoolClass(20L, school(5L))));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enroll(STUDENT_ID, "  abcd2345 ");

        assertThat(response.classId()).isEqualTo(20L);
    }

    @Test
    void enroll_studentAlreadyInTheSchoolOfTheClass_keepsTheSchool() {
        School school = school(5L);
        User student = student(school);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(classRepository.findJoinableByJoinCode(CODE)).thenReturn(Optional.of(schoolClass(20L, school(5L))));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enroll(STUDENT_ID, CODE);

        assertThat(student.getSchool()).isSameAs(school);
        assertThat(student.getSchoolClass().getId()).isEqualTo(20L);
    }

    @Test
    void enroll_blockedStudent_isRejectedBeforeAnythingIsLookedUp() {
        doThrow(new TooManyAttemptsException(600)).when(attemptLimiter).checkAllowed(STUDENT_ID);

        assertThatThrownBy(() -> service.enroll(STUDENT_ID, CODE)).isInstanceOf(TooManyAttemptsException.class);

        verifyNoInteractions(userRepository, classRepository);
    }

    @Test
    void enroll_alreadyEnrolledStudent_isRejectedWithoutLookingUpTheCodeOrCountingAFailure() {
        User student = student(school(5L));
        student.setSchoolClass(schoolClass(20L, school(5L)));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.enroll(STUDENT_ID, CODE)).isInstanceOf(AlreadyEnrolledException.class);

        verifyNoInteractions(classRepository);
        verify(attemptLimiter, never()).recordFailure(any());
    }

    @Test
    void enroll_unknownOrInactiveCode_countsAsAFailure() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student(null)));
        when(classRepository.findJoinableByJoinCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(STUDENT_ID, CODE)).isInstanceOf(InvalidJoinCodeException.class);

        verify(attemptLimiter).recordFailure(STUDENT_ID);
        verify(attemptLimiter, never()).recordSuccess(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void enroll_classOfAnotherSchool_isRejectedCountsAsAFailureAndChangesNothing() {
        User student = student(school(9L));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(classRepository.findJoinableByJoinCode(CODE)).thenReturn(Optional.of(schoolClass(20L, school(5L))));

        assertThatThrownBy(() -> service.enroll(STUDENT_ID, CODE)).isInstanceOf(SchoolMismatchException.class);

        verify(attemptLimiter).recordFailure(STUDENT_ID);
        assertThat(student.getSchoolClass()).isNull();
        assertThat(student.getSchool().getId()).isEqualTo(9L);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void enroll_unknownStudent_throwsNotFound() {
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(STUDENT_ID, CODE)).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(classRepository);
    }

    // ---------- helpers ----------

    private static User student(School school) {
        User u = new User();
        u.setId(STUDENT_ID);
        u.setName("Jano");
        u.setSurname("Novak");
        u.setEmail("student@example.com");
        u.setRole(UserRole.STUDENT);
        u.setActive(true);
        u.setSchool(school);
        return u;
    }

    private static School school(Long id) {
        School s = new School();
        s.setId(id);
        s.setName("School " + id);
        s.setAddress("Address");
        s.setActive(true);
        return s;
    }

    private static SchoolClass schoolClass(Long id, School school) {
        SchoolClass c = new SchoolClass();
        c.setId(id);
        c.setSchool(school);
        c.setName("Class " + id);
        c.setJoinCode(CODE);
        c.setActive(true);
        return c;
    }
}