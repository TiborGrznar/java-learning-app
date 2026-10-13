package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.EnrollmentResponse;
import com.tgrznar.javalearningapp.schoolclass.exception.AlreadyEnrolledException;
import com.tgrznar.javalearningapp.schoolclass.exception.InvalidJoinCodeException;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolMismatchException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * A student joins a class with its join code. The join code is a credential, so it is never logged, and an
 * unknown code, an inactive class and an inactive school are not told apart. The order of the checks matters:
 * the attempt limit comes first, and an already enrolled student is turned away before any code is looked up,
 * so that nobody can use this endpoint to test codes for free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final UserRepository userRepository;
    private final SchoolClassRepository classRepository;
    private final EnrollmentAttemptLimiter attemptLimiter;

    @Transactional
    public EnrollmentResponse enroll(Long studentId, String joinCode) {
        attemptLimiter.checkAllowed(studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException(studentId));
        if (student.getSchoolClass() != null) {
            throw new AlreadyEnrolledException();
        }

        String code = joinCode.trim().toUpperCase(Locale.ROOT);
        SchoolClass schoolClass = classRepository.findJoinableByJoinCode(code).orElse(null);
        if (schoolClass == null) {
            attemptLimiter.recordFailure(studentId);
            throw new InvalidJoinCodeException();
        }

        School school = schoolClass.getSchool();
        School current = student.getSchool();
        if (current != null && !current.getId().equals(school.getId())) {
            // A valid code of a foreign school also counts as a failure, so it cannot be used to probe codes.
            attemptLimiter.recordFailure(studentId);
            throw new SchoolMismatchException();
        }

        if (current == null) {
            student.setSchool(school);
        }
        student.setSchoolClass(schoolClass);
        userRepository.saveAndFlush(student);

        attemptLimiter.recordSuccess(studentId);
        log.info("Student enrolled: studentId={}, classId={}", studentId, schoolClass.getId());
        return EnrollmentResponse.from(schoolClass);
    }
}