package com.tgrznar.javalearningapp.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * The OpenAPI description and Swagger UI describe every endpoint and are open to anyone while they are on.
 * The default profile is "dev", so a deployment that forgets to set a profile must not expose them. This
 * reads the raw application.yaml (placeholders not resolved), so it does not depend on the environment
 * of the machine that runs the tests.
 */
class ApiDocsDefaultsTest {

    @ParameterizedTest
    @ValueSource(strings = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"})
    void documentation_isOffUnlessTheEnvironmentVariableTurnsItOn(String property) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty(property)).isEqualTo("${API_DOCS_ENABLED:false}");
    }
}
