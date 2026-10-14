package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;

import java.util.Locale;
import java.util.UUID;

/**
 * Saves a school, its teacher and a class, so a test student can be placed into a class.
 * Learning endpoints are open only to students who joined a class. Everything is unique per call (UUID)
 * and lives in the test transaction, which is rolled back.
 */
public final class EnrolledClassFactory {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository classRepository;

    public EnrolledClassFactory(UserRepository userRepository,
                                SchoolRepository schoolRepository,
                                SchoolClassRepository classRepository) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.classRepository = classRepository;
    }

    /** A new class in a new active school, led by a new teacher. */
    public SchoolClass saveClass(boolean active) {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(true);
        school = schoolRepository.saveAndFlush(school);

        User teacher = new User();
        teacher.setName("Petra");
        teacher.setSurname("Ucitelova");
        teacher.setEmail("teacher-" + UUID.randomUUID() + "@example.com");
        // Not a valid BCrypt hash: this user never logs in.
        teacher.setPasswordHash("not-a-real-hash");
        teacher.setRole(UserRole.TEACHER);
        teacher.setSchool(school);
        teacher.setActive(true);
        teacher = userRepository.saveAndFlush(teacher);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName("Class " + UUID.randomUUID().toString().substring(0, 8));
        schoolClass.setJoinCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        schoolClass.setActive(active);
        return classRepository.saveAndFlush(schoolClass);
    }
}