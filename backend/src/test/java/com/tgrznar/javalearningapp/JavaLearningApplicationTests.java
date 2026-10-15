package com.tgrznar.javalearningapp;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JavaLearningApplicationTests {

    @Test
    void contextLoads() {
    }

}