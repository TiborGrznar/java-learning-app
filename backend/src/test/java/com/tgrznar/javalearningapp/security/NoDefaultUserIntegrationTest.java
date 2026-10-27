package com.tgrznar.javalearningapp.security;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Passwords are checked by AuthService only. Spring Boot must not create its default in-memory user
 * with a random password (it is printed to the log at start and would work if basic login were enabled).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoDefaultUserIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void noInMemoryUserIsCreated() {
        assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
    }

    @Test
    void userDetailsServiceFindsNobody() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}