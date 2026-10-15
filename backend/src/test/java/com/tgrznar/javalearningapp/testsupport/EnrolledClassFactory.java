package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.boot.test.context.TestComponent;

import java.util.Locale;
import java.util.UUID;

/**
 * Saves a school, its teacher and a class, so a test student can be placed into a class.
 * Learning endpoints are open only to students who joined a class. Everything is unique per call (UUID)
 * and lives in the test transaction, which is rolled back.
 */
@TestComponent
public class EnrolledClassFactory {

    private final TestUsers testUsers;
    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository classRepository;

    public EnrolledClassFactory(TestUsers testUsers,
                                SchoolRepository schoolRepository,
                                SchoolClassRepository classRepository) {
        this.testUsers = testUsers;
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

        User teacher = testUsers.teacher(school);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName("Class " + UUID.randomUUID().toString().substring(0, 8));
        schoolClass.setJoinCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        schoolClass.setActive(active);
        return classRepository.saveAndFlush(schoolClass);
    }

    /** A student who already joined a new active class (and the school of that class). */
    public User saveEnrolledStudent() {
        return testUsers.user(UserRole.STUDENT).inClass(saveClass(true)).save();
    }
}