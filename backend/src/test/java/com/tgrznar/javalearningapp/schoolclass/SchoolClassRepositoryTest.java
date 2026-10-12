package com.tgrznar.javalearningapp.schoolclass;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SchoolClassRepositoryTest {

    @Autowired
    private SchoolClassRepository classRepository;
    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void save_persistsAllFields() {
        School school = createSchool();
        User teacher = createUser(UserRole.TEACHER, school);
        SchoolClass saved = classRepository.saveAndFlush(newClass(school, teacher, "3.A", "ABCD2345"));
        entityManager.clear();

        SchoolClass loaded = classRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("3.A");
        assertThat(loaded.getJoinCode()).isEqualTo("ABCD2345");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getSchool().getId()).isEqualTo(school.getId());
        assertThat(loaded.getTeacher().getId()).isEqualTo(teacher.getId());
    }

    @Test
    void save_duplicateJoinCode_isRejected() {
        School school = createSchool();
        User teacher = createUser(UserRole.TEACHER, school);
        classRepository.saveAndFlush(newClass(school, teacher, "3.A", "ABCD2345"));

        assertThatThrownBy(() -> classRepository.saveAndFlush(newClass(school, teacher, "3.B", "ABCD2345")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_joinCodeDifferingOnlyInCase_isRejected() {
        School school = createSchool();
        User teacher = createUser(UserRole.TEACHER, school);
        classRepository.saveAndFlush(newClass(school, teacher, "3.A", "ABCD2345"));

        assertThatThrownBy(() -> classRepository.saveAndFlush(newClass(school, teacher, "3.B", "abcd2345")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_duplicateNameInSameSchool_isRejected() {
        School school = createSchool();
        User teacher = createUser(UserRole.TEACHER, school);
        classRepository.saveAndFlush(newClass(school, teacher, "3.A", "ABCD2345"));

        assertThatThrownBy(() -> classRepository.saveAndFlush(newClass(school, teacher, "3.A", "WXYZ6789")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_sameNameInDifferentSchool_isAllowed() {
        School firstSchool = createSchool();
        School secondSchool = createSchool();
        User firstTeacher = createUser(UserRole.TEACHER, firstSchool);
        User secondTeacher = createUser(UserRole.TEACHER, secondSchool);
        classRepository.saveAndFlush(newClass(firstSchool, firstTeacher, "3.A", "ABCD2345"));

        SchoolClass second = classRepository.saveAndFlush(newClass(secondSchool, secondTeacher, "3.A", "WXYZ6789"));

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void user_linkedToClass_persistsClassId() {
        School school = createSchool();
        User teacher = createUser(UserRole.TEACHER, school);
        SchoolClass schoolClass = classRepository.saveAndFlush(newClass(school, teacher, "3.A", "ABCD2345"));
        User student = createUser(UserRole.STUDENT, school);

        student.setSchoolClass(schoolClass);
        userRepository.saveAndFlush(student);
        entityManager.clear();

        User loaded = userRepository.findById(student.getId()).orElseThrow();
        assertThat(loaded.getSchoolClass().getId()).isEqualTo(schoolClass.getId());
    }

    private School createSchool() {
        School school = new School();
        school.setName("School " + UUID.randomUUID());
        school.setAddress("Test street 1");
        return schoolRepository.saveAndFlush(school);
    }

    private User createUser(UserRole role, School school) {
        User user = new User();
        user.setName("Test");
        user.setSurname("User");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        user.setSchool(school);
        return userRepository.saveAndFlush(user);
    }

    private SchoolClass newClass(School school, User teacher, String name, String joinCode) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName(name);
        schoolClass.setJoinCode(joinCode);
        return schoolClass;
    }
}