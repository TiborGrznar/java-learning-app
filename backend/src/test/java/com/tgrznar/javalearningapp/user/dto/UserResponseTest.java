package com.tgrznar.javalearningapp.user.dto;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @Test
    void from_userInClass_includesClassIdAndName() {
        School school = new School();
        school.setId(1L);
        school.setName("Test school");
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(7L);
        schoolClass.setName("3.A");
        User user = newStudent();
        user.setSchool(school);
        user.setSchoolClass(schoolClass);

        UserResponse response = UserResponse.from(user);

        assertThat(response.classId()).isEqualTo(7L);
        assertThat(response.className()).isEqualTo("3.A");
        assertThat(response.schoolId()).isEqualTo(1L);
    }

    @Test
    void from_userWithoutClass_hasNullClassFields() {
        UserResponse response = UserResponse.from(newStudent());

        assertThat(response.classId()).isNull();
        assertThat(response.className()).isNull();
    }

    private User newStudent() {
        User user = new User();
        user.setId(10L);
        user.setName("Test");
        user.setSurname("Student");
        user.setEmail("student@example.com");
        user.setRole(UserRole.STUDENT);
        return user;
    }
}