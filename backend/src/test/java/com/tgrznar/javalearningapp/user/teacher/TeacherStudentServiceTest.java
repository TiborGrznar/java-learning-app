package com.tgrznar.javalearningapp.user.teacher;

import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.progress.StudentProgressService;
import com.tgrznar.javalearningapp.learning.progress.dto.ProgressResponse;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.user.teacher.dto.ModuleCell;
import com.tgrznar.javalearningapp.user.teacher.dto.ModuleHeader;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewResponse;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewRow;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentsActiveResponse;
import com.tgrznar.javalearningapp.user.teacher.dto.TeacherStudentProgressResponse;
import com.tgrznar.javalearningapp.userprogress.ProgressFlagsRow;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link TeacherStudentService}: who may call it, how the module matrix is built and how the bulk
 * change behaves. The scope itself (which students a teacher sees) is a database query and is covered by the
 * API tests against a real MySQL.
 */
@ExtendWith(MockitoExtension.class)
class TeacherStudentServiceTest {

    private static final Long TEACHER_ID = 7L;
    private static final Long SCHOOL_ID = 3L;
    private static final Sort COURSE_ORDER = Sort.by("orderNumber", "id");

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private CourseModuleRepository moduleRepository;

    @Mock
    private UserProgressRepository progressRepository;

    @Mock
    private StudentProgressService studentProgressService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private TeacherStudentService service;

    @BeforeEach
    void setUp() {
        service = new TeacherStudentService(userRepository, classRepository, moduleRepository,
                progressRepository, studentProgressService, refreshTokenService);
    }

    // ---------- who may call it ----------

    @Test
    void overview_teacherDoesNotExist_isDenied() {
        when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.overview(TEACHER_ID, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verify(userRepository).findById(TEACHER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(classRepository, moduleRepository, progressRepository);
    }

    @Test
    void overview_callerIsNotATeacher_isDenied() {
        when(userRepository.findById(TEACHER_ID))
                .thenReturn(Optional.of(user(TEACHER_ID, UserRole.ADMIN, true, school())));

        assertThatThrownBy(() -> service.overview(TEACHER_ID, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(classRepository, moduleRepository, progressRepository);
    }

    @Test
    void overview_inactiveTeacher_isDeniedEvenWithAValidToken() {
        when(userRepository.findById(TEACHER_ID))
                .thenReturn(Optional.of(user(TEACHER_ID, UserRole.TEACHER, false, school())));

        assertThatThrownBy(() -> service.overview(TEACHER_ID, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(classRepository, moduleRepository, progressRepository);
    }

    @Test
    void overview_teacherWithoutSchool_isDenied() {
        when(userRepository.findById(TEACHER_ID))
                .thenReturn(Optional.of(user(TEACHER_ID, UserRole.TEACHER, true, null)));

        assertThatThrownBy(() -> service.overview(TEACHER_ID, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(classRepository, moduleRepository, progressRepository);
    }

    @Test
    void setActive_inactiveTeacher_isDeniedAndChangesNothing() {
        when(userRepository.findById(TEACHER_ID))
                .thenReturn(Optional.of(user(TEACHER_ID, UserRole.TEACHER, false, school())));

        assertThatThrownBy(() -> service.setActive(TEACHER_ID, List.of(10L), false))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(refreshTokenService);
    }

    // ---------- overview ----------

    @Test
    void overview_classOutsideTheTeachersScope_isNotFoundAndNoStudentsAreLoaded() {
        teacherLoaded();
        when(classRepository.existsByIdAndTeacherIdAndSchoolId(55L, TEACHER_ID, SCHOOL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.overview(TEACHER_ID, 55L, null, 0, 20))
                .isInstanceOf(SchoolClassNotFoundException.class);

        verifyNoInteractions(moduleRepository, progressRepository);
    }

    @Test
    void overview_buildsTheMatrixFromProgressFlags() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User first = student(10L, "Adamova", schoolClass, true);
        User second = student(11L, "Bednarova", schoolClass, false);
        CourseModule moduleOne = module(1L, "Basics", 1);
        CourseModule moduleTwo = module(2L, "Loops", 2);
        stubPage(0, 20, List.of(first, second));
        when(moduleRepository.findByActiveTrue(COURSE_ORDER)).thenReturn(List.of(moduleOne, moduleTwo));
        when(progressRepository.findFlagsByUserIdIn(List.of(10L, 11L))).thenReturn(List.of(
                new ProgressFlagsRow(10L, 1L, true, true, true),
                new ProgressFlagsRow(10L, 2L, true, false, false)));

        StudentOverviewResponse result = service.overview(TEACHER_ID, null, null, 0, 20);

        assertThat(result.modules()).containsExactly(
                new ModuleHeader(1L, "Basics", 1),
                new ModuleHeader(2L, "Loops", 2));
        assertThat(result.students().totalElements()).isEqualTo(2);

        StudentOverviewRow row = result.students().content().get(0);
        assertThat(row.studentId()).isEqualTo(10L);
        assertThat(row.name()).isEqualTo("Jano");
        assertThat(row.surname()).isEqualTo("Adamova");
        assertThat(row.email()).isEqualTo("student10@example.com");
        assertThat(row.active()).isTrue();
        assertThat(row.classId()).isEqualTo(40L);
        assertThat(row.className()).isEqualTo("1.A");
        assertThat(row.classActive()).isTrue();
        assertThat(row.completedModules()).isEqualTo(1);
        assertThat(row.modules()).containsExactly(
                new ModuleCell(1L, ModuleStatus.COMPLETED, true, true, true),
                new ModuleCell(2L, ModuleStatus.IN_PROGRESS, true, false, false));
    }

    @Test
    void overview_studentWithoutProgressRows_hasEveryModuleNotStarted() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", false);
        User student = student(11L, "Bednarova", schoolClass, false);
        stubPage(0, 20, List.of(student));
        when(moduleRepository.findByActiveTrue(COURSE_ORDER))
                .thenReturn(List.of(module(1L, "Basics", 1), module(2L, "Loops", 2)));
        when(progressRepository.findFlagsByUserIdIn(List.of(11L))).thenReturn(List.of());

        StudentOverviewRow row = service.overview(TEACHER_ID, null, null, 0, 20).students().content().get(0);

        assertThat(row.completedModules()).isZero();
        assertThat(row.active()).isFalse();
        assertThat(row.classActive()).isFalse();
        assertThat(row.modules()).containsExactly(
                new ModuleCell(1L, ModuleStatus.NOT_STARTED, false, false, false),
                new ModuleCell(2L, ModuleStatus.NOT_STARTED, false, false, false));
    }

    @Test
    void overview_emptyPage_skipsTheProgressQueryButKeepsTheModuleHeaders() {
        teacherLoaded();
        stubPage(0, 20, List.of());
        when(moduleRepository.findByActiveTrue(COURSE_ORDER)).thenReturn(List.of(module(1L, "Basics", 1)));

        StudentOverviewResponse result = service.overview(TEACHER_ID, null, null, 0, 20);

        assertThat(result.modules()).hasSize(1);
        assertThat(result.students().content()).isEmpty();
        assertThat(result.students().totalElements()).isZero();
        verifyNoInteractions(progressRepository);
    }

    @Test
    void overview_correctsOutOfRangePageAndSizeInsteadOfRejectingThem() {
        teacherLoaded();
        when(userRepository.findTeacherStudents(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty((Pageable) invocation.getArgument(5)));
        when(moduleRepository.findByActiveTrue(COURSE_ORDER)).thenReturn(List.of());

        var negativePage = service.overview(TEACHER_ID, null, null, -3, 0).students();
        var hugeSize = service.overview(TEACHER_ID, null, null, 2, 100_000).students();

        assertThat(negativePage.page()).isZero();
        assertThat(negativePage.size()).isEqualTo(TeacherStudentService.DEFAULT_PAGE_SIZE);
        assertThat(hugeSize.page()).isEqualTo(2);
        assertThat(hugeSize.size()).isEqualTo(TeacherStudentService.MAX_PAGE_SIZE);
    }

    // ---------- progress detail ----------

    @Test
    void progress_returnsTheSummaryAndExactlyWhatTheStudentSees() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User student = student(10L, "Adamova", schoolClass, true);
        ProgressResponse progress = new ProgressResponse(0, 0, List.of());
        when(userRepository.findTeacherStudent(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, 10L))
                .thenReturn(Optional.of(student));
        when(studentProgressService.get(10L)).thenReturn(progress);

        TeacherStudentProgressResponse result = service.progress(TEACHER_ID, 10L);

        assertThat(result.progress()).isSameAs(progress);
        assertThat(result.student().studentId()).isEqualTo(10L);
        assertThat(result.student().name()).isEqualTo("Jano");
        assertThat(result.student().surname()).isEqualTo("Adamova");
        assertThat(result.student().email()).isEqualTo("student10@example.com");
        assertThat(result.student().active()).isTrue();
        assertThat(result.student().classId()).isEqualTo(40L);
        assertThat(result.student().className()).isEqualTo("1.A");
        assertThat(result.student().classActive()).isTrue();
    }

    @Test
    void progress_studentOutsideTheScope_isNotFoundAndNoProgressIsLoaded() {
        teacherLoaded();
        when(userRepository.findTeacherStudent(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.progress(TEACHER_ID, 10L))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(studentProgressService);
    }

    // ---------- bulk change ----------

    @Test
    void setActive_oneIdOutsideTheScope_isNotFoundAndNothingIsChanged() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User own = student(10L, "Adamova", schoolClass, true);
        when(userRepository.findTeacherStudentsForUpdate(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, List.of(10L, 11L)))
                .thenReturn(List.of(own));

        assertThatThrownBy(() -> service.setActive(TEACHER_ID, List.of(10L, 11L), false))
                .isInstanceOf(UserNotFoundException.class);

        assertThat(own.isActive()).isTrue();
        verify(userRepository, never()).flush();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_deactivation_revokesTokensOnlyOfChangedStudentsAndReportsTheRest() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User first = student(10L, "Adamova", schoolClass, true);
        User alreadyInactive = student(11L, "Bednarova", schoolClass, false);
        User third = student(12L, "Cernakova", schoolClass, true);
        when(userRepository.findTeacherStudentsForUpdate(
                TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, List.of(10L, 11L, 12L)))
                .thenReturn(List.of(first, alreadyInactive, third));

        StudentsActiveResponse result = service.setActive(TEACHER_ID, List.of(10L, 11L, 12L), false);

        assertThat(result.active()).isFalse();
        assertThat(result.changed()).containsExactly(10L, 12L);
        assertThat(result.unchanged()).containsExactly(11L);
        assertThat(first.isActive()).isFalse();
        assertThat(alreadyInactive.isActive()).isFalse();
        assertThat(third.isActive()).isFalse();

        // The change is written before the tokens are revoked.
        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).flush();
        order.verify(refreshTokenService).revokeAllForUser(10L);
        order.verify(refreshTokenService).revokeAllForUser(12L);
        verify(refreshTokenService, never()).revokeAllForUser(11L);
    }

    @Test
    void setActive_reactivation_doesNotTouchRefreshTokens() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User inactive = student(10L, "Adamova", schoolClass, false);
        User active = student(11L, "Bednarova", schoolClass, true);
        when(userRepository.findTeacherStudentsForUpdate(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, List.of(10L, 11L)))
                .thenReturn(List.of(inactive, active));

        StudentsActiveResponse result = service.setActive(TEACHER_ID, List.of(10L, 11L), true);

        assertThat(result.active()).isTrue();
        assertThat(result.changed()).containsExactly(10L);
        assertThat(result.unchanged()).containsExactly(11L);
        assertThat(inactive.isActive()).isTrue();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_repeatedIdsAreMergedAndSortedBeforeTheLockedQuery() {
        teacherLoaded();
        SchoolClass schoolClass = schoolClass(40L, "1.A", true);
        User first = student(10L, "Adamova", schoolClass, true);
        User third = student(12L, "Cernakova", schoolClass, true);
        when(userRepository.findTeacherStudentsForUpdate(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, List.of(10L, 12L)))
                .thenReturn(List.of(first, third));

        StudentsActiveResponse result = service.setActive(TEACHER_ID, List.of(12L, 10L, 12L, 10L), false);

        assertThat(result.changed()).containsExactly(10L, 12L);
        assertThat(result.unchanged()).isEmpty();
        verify(refreshTokenService).revokeAllForUser(10L);
        verify(refreshTokenService).revokeAllForUser(12L);
        verifyNoMoreInteractions(refreshTokenService);
    }

    // ---------- fixtures ----------

    private void teacherLoaded() {
        when(userRepository.findById(TEACHER_ID))
                .thenReturn(Optional.of(user(TEACHER_ID, UserRole.TEACHER, true, school())));
    }

    private void stubPage(int page, int size, List<User> students) {
        PageRequest pageable = PageRequest.of(page, size);
        when(userRepository.findTeacherStudents(TEACHER_ID, SCHOOL_ID, UserRole.STUDENT, null, null, pageable))
                .thenReturn(new PageImpl<>(students, pageable, students.size()));
    }

    private static School school() {
        School school = new School();
        school.setId(SCHOOL_ID);
        school.setName("School");
        school.setActive(true);
        return school;
    }

    private static User user(Long id, UserRole role, boolean active, School school) {
        User user = new User();
        user.setId(id);
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail("user" + id + "@example.com");
        user.setRole(role);
        user.setActive(active);
        user.setSchool(school);
        return user;
    }

    private static User student(Long id, String surname, SchoolClass schoolClass, boolean active) {
        User student = user(id, UserRole.STUDENT, active, school());
        student.setEmail("student" + id + "@example.com");
        student.setSurname(surname);
        student.setSchoolClass(schoolClass);
        return student;
    }

    private static SchoolClass schoolClass(Long id, String name, boolean active) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setName(name);
        schoolClass.setActive(active);
        return schoolClass;
    }

    private static CourseModule module(Long id, String title, int orderNumber) {
        CourseModule module = new CourseModule();
        module.setId(id);
        module.setTitle(title);
        module.setOrderNumber(orderNumber);
        module.setActive(true);
        return module;
    }
}
