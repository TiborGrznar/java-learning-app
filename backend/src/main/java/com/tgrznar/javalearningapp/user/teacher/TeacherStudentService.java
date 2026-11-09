package com.tgrznar.javalearningapp.user.teacher;

import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.user.teacher.dto.ModuleCell;
import com.tgrznar.javalearningapp.user.teacher.dto.ModuleHeader;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewResponse;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewRow;
import com.tgrznar.javalearningapp.userprogress.ProgressFlagsRow;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A teacher's view of the students in his own classes (UC-11).
 * <p>
 * The scope is decided here and only here: a student is visible when his class is led by the calling teacher
 * <b>and</b> belongs to the teacher's current school. The school condition matters because a teacher who leads
 * only deactivated classes can be moved to another school while the classes keep him as their teacher. A student
 * outside the scope, a class outside the scope and an unknown id are indistinguishable for the caller.
 * <p>
 * The teacher is loaded from the database on every call and has to be an active teacher with a school. The access
 * token stays valid for some minutes after a deactivation or a change of role, so it cannot be trusted alone.
 * <p>
 * The number of queries does not depend on the number of students: the teacher, the page of students (with its
 * count), the active modules and the progress flags of the students on the page.
 */
@Service
@RequiredArgsConstructor
public class TeacherStudentService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final Sort COURSE_ORDER = Sort.by("orderNumber", "id");

    private final UserRepository userRepository;
    private final SchoolClassRepository classRepository;
    private final CourseModuleRepository moduleRepository;
    private final UserProgressRepository progressRepository;

    /**
     * One page of the students of the teacher's own classes with their state in every active module.
     * A null filter means "any". Out-of-range page and size are corrected instead of rejected.
     */
    @Transactional(readOnly = true)
    public StudentOverviewResponse overview(Long teacherId, Long classId, Boolean active, int page, int size) {
        Long schoolId = requireTeacherSchoolId(teacherId);
        if (classId != null && !classRepository.existsByIdAndTeacherIdAndSchoolId(classId, teacherId, schoolId)) {
            throw new SchoolClassNotFoundException(classId);
        }

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<User> students = userRepository.findTeacherStudents(
                teacherId, schoolId, UserRole.STUDENT, classId, active, pageable);

        List<CourseModule> modules = moduleRepository.findByActiveTrue(COURSE_ORDER);
        Map<Long, Map<Long, ProgressFlagsRow>> flags = flagsByStudentAndModule(students.getContent());

        List<ModuleHeader> headers = modules.stream()
                .map(module -> new ModuleHeader(module.getId(), module.getTitle(), module.getOrderNumber()))
                .toList();
        Page<StudentOverviewRow> rows = students.map(student -> toRow(
                student, modules, flags.getOrDefault(student.getId(), Map.of())));

        return new StudentOverviewResponse(headers, PageResponse.from(rows));
    }

    /**
     * The id of the teacher's school. Fails closed: whoever is not an active teacher with a school gets
     * ACCESS_DENIED, whatever the token says.
     */
    private Long requireTeacherSchoolId(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new AccessDeniedException("The signed-in user does not exist"));
        if (teacher.getRole() != UserRole.TEACHER || !teacher.isActive() || teacher.getSchool() == null) {
            throw new AccessDeniedException("The signed-in user is not an active teacher with a school");
        }
        return teacher.getSchool().getId();
    }

    /** Progress flags of the given students in one query (none when the page is empty). */
    private Map<Long, Map<Long, ProgressFlagsRow>> flagsByStudentAndModule(List<User> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> studentIds = students.stream().map(User::getId).toList();
        return progressRepository.findFlagsByUserIdIn(studentIds).stream()
                .collect(Collectors.groupingBy(
                        ProgressFlagsRow::userId,
                        Collectors.toMap(ProgressFlagsRow::moduleId, Function.identity())));
    }

    private static StudentOverviewRow toRow(User student,
                                            List<CourseModule> modules,
                                            Map<Long, ProgressFlagsRow> flagsByModule) {
        List<ModuleCell> cells = modules.stream()
                .map(module -> toCell(module.getId(), flagsByModule.get(module.getId())))
                .toList();
        int completed = (int) cells.stream().filter(cell -> cell.status() == ModuleStatus.COMPLETED).count();

        // The class is fetched together with the page, so this does not query.
        SchoolClass schoolClass = student.getSchoolClass();
        return new StudentOverviewRow(
                student.getId(),
                student.getName(),
                student.getSurname(),
                student.getEmail(),
                student.isActive(),
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.isActive(),
                completed,
                cells);
    }

    /** A student without a progress row has not started the module. */
    private static ModuleCell toCell(Long moduleId, ProgressFlagsRow flags) {
        if (flags == null) {
            return new ModuleCell(moduleId, ModuleStatus.NOT_STARTED, false, false, false);
        }
        boolean theory = flags.theoryCompleted();
        boolean quiz = flags.quizCompleted();
        boolean coding = flags.codingCompleted();
        return new ModuleCell(moduleId, ModuleStatus.of(theory, quiz, coding), theory, quiz, coding);
    }
}
