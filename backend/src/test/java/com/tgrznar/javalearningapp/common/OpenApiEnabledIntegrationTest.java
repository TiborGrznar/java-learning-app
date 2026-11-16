package com.tgrznar.javalearningapp.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The OpenAPI description with API_DOCS_ENABLED=true (the variable that application.yaml maps to springdoc). */
@SpringBootTest(properties = "API_DOCS_ENABLED=true")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class OpenApiEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void description_isServedWithoutToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Java Learning App API"));
    }

    @Test
    void bearerScheme_isDeclaredAndRequiredByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"})
    void publicEndpoints_haveNoSecurityRequirement(String path) throws Exception {
        // Also proves the path still exists: a renamed endpoint makes the JSON path below fail.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + path + "'].post.security").isEmpty());
    }

    @Test
    void protectedEndpoint_listsTheStandardErrorAnswers() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['500']").exists());
    }

    @Test
    void publicEndpoint_hasTheRateLimitAnswer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['500']").exists());
    }

    @Test
    void errorSchema_hasCodeMessageAndFieldErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.fieldErrors").exists());
    }

    @Test
    void swaggerUi_isServedWithoutToken() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
