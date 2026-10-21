package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.boot.test.context.TestComponent;

import java.util.UUID;

/**
 * Creates users for tests. The defaults (name, surname, unique e-mail, a hash nobody can log in
 * with) live here only, so a new required column of {@link User} is fixed in one place.
 * Everything is saved in the test transaction, which is rolled back.
 */
@TestComponent
public class TestUsers {

    private final UserRepository userRepository;

    public TestUsers(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Starts a user of the given role: {@code testUsers.user(UserRole.STUDENT).inClass(c).save()}. */
    public Builder user(UserRole role) {
        return new Builder(role);
    }

    /** An active teacher of the given school. */
    public User teacher(School school) {
        return user(UserRole.TEACHER).school(school).save();
    }

    public final class Builder {

        private final UserRole role;
        private String name = "Jano";
        private String surname = "Novak";
        private String email = "user-" + UUID.randomUUID() + "@example.com";
        private School school;
        private SchoolClass schoolClass;
        private boolean active = true;

        private Builder(UserRole role) {
            this.role = role;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder surname(String surname) {
            this.surname = surname;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder school(School school) {
            this.school = school;
            return this;
        }

        /** Puts the user into the class and into the school of that class. */
        public Builder inClass(SchoolClass schoolClass) {
            this.schoolClass = schoolClass;
            this.school = schoolClass.getSchool();
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        /** The user without saving it. */
        public User build() {
            User user = new User();
            user.setName(name);
            user.setSurname(surname);
            user.setEmail(email);
            // Not a valid BCrypt hash: these users never log in, they only get a token.
            user.setPasswordHash("not-a-real-hash");
            user.setRole(role);
            user.setSchool(school);
            user.setSchoolClass(schoolClass);
            user.setActive(active);
            return user;
        }

        public User save() {
            return userRepository.saveAndFlush(build());
        }
    }
}