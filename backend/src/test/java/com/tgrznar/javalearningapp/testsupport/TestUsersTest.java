package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TestUsersTest {

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void save_usesDefaults() {
        User user = testUsers.user(UserRole.STUDENT).save();
        entityManager.clear();

        User loaded = userRepository.findById(user.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Jano");
        assertThat(loaded.getSurname()).isEqualTo("Novak");
        assertThat(loaded.getEmail()).startsWith("user-").endsWith("@example.com").isLowerCase();
        assertThat(loaded.getPasswordHash()).isNotBlank();
        assertThat(loaded.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getSchool()).isNull();
        assertThat(loaded.getSchoolClass()).isNull();
    }

    @Test
    void save_twoUsersGetDifferentEmails() {
        User first = testUsers.user(UserRole.STUDENT).save();
        User second = testUsers.user(UserRole.STUDENT).save();

        assertThat(first.getEmail()).isNotEqualTo(second.getEmail());
    }

    @Test
    void save_appliesOverrides() {
        SchoolClass schoolClass = enrolledClass();

        User user = testUsers.user(UserRole.TEACHER)
                .name("Petra")
                .surname("Kovacova")
                .email("petra@example.com")
                .school(schoolClass.getSchool())
                .active(false)
                .save();
        entityManager.clear();

        User loaded = userRepository.findById(user.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Petra");
        assertThat(loaded.getSurname()).isEqualTo("Kovacova");
        assertThat(loaded.getEmail()).isEqualTo("petra@example.com");
        assertThat(loaded.getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getSchool().getId()).isEqualTo(schoolClass.getSchool().getId());
    }

    @Test
    void inClass_setsTheClassAndItsSchool() {
        SchoolClass schoolClass = enrolledClass();

        User student = testUsers.user(UserRole.STUDENT).inClass(schoolClass).save();
        entityManager.clear();

        User loaded = userRepository.findById(student.getId()).orElseThrow();

        assertThat(loaded.getSchoolClass().getId()).isEqualTo(schoolClass.getId());
        assertThat(loaded.getSchool().getId()).isEqualTo(schoolClass.getSchool().getId());
    }

    @Test
    void teacher_isActiveTeacherOfTheSchool() {
        SchoolClass schoolClass = enrolledClass();

        User teacher = testUsers.teacher(schoolClass.getSchool());

        assertThat(teacher.getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(teacher.isActive()).isTrue();
        assertThat(teacher.getSchool().getId()).isEqualTo(schoolClass.getSchool().getId());
    }

    @Test
    void build_doesNotSaveTheUser() {
        User user = testUsers.user(UserRole.ADMIN).build();

        assertThat(user.getId()).isNull();
        assertThat(userRepository.existsByEmail(user.getEmail())).isFalse();
    }

    private SchoolClass enrolledClass() {
        return enrolledClasses.saveClass(true);
    }
}