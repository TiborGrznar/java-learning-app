package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Shared mocks, constants and fixtures of the AdminUserService unit tests. Each test class covers one
 * operation of the service and extends this class, so the mocks are declared once.
 */
@ExtendWith(MockitoExtension.class)
abstract class AdminUserServiceTestBase {

    protected static final Long ADMIN_ID = 99L;
    protected static final String TEMP_PASSWORD = "TempPass12345678";

    @Mock
    protected UserRepository userRepository;

    @Mock
    protected SchoolRepository schoolRepository;

    @Mock
    protected PasswordEncoder passwordEncoder;

    @Mock
    protected TemporaryPasswordGenerator passwordGenerator;

    @Mock
    protected RefreshTokenService refreshTokenService;

    @Mock
    protected SchoolClassRepository classRepository;

    @InjectMocks
    protected AdminUserService service;

    protected static User user(Long id, UserRole role, boolean active, School school) {
        User u = new User();
        u.setId(id);
        u.setName("Jano");
        u.setSurname("Novak");
        u.setEmail("jano" + id + "@example.com");
        u.setRole(role);
        u.setActive(active);
        u.setSchool(school);
        return u;
    }

    protected static School school(Long id, boolean active) {
        School s = new School();
        s.setId(id);
        s.setName("School " + id);
        s.setAddress("Address");
        s.setActive(active);
        return s;
    }

    protected static SchoolClass schoolClass(Long id) {
        SchoolClass c = new SchoolClass();
        c.setId(id);
        c.setName("Class " + id);
        return c;
    }
}