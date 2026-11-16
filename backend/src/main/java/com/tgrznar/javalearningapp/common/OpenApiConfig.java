package com.tgrznar.javalearningapp.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Shape of the OpenAPI description (served at /v3/api-docs while API_DOCS_ENABLED=true, see application.yaml).
 * <p>
 * What it adds on top of what springdoc reads from the controllers:
 * <ul>
 *   <li>a bearer (JWT) security scheme that every operation requires, so Swagger UI offers an Authorize button,</li>
 *   <li>no security requirement on the four public endpoints under /api/v1/auth (the same list as in SecurityConfig;
 *       OpenApiEnabledIntegrationTest fails if a path here stops existing),</li>
 *   <li>the uniform error body {@code ErrorResponse} and the answers every endpoint can give regardless of its
 *       own logic: 429 from the rate limiter, 500, and 401 and 403 on the protected endpoints. A frontend type
 *       generator then sees the real error shape. The list of error codes stays in README.md and in
 *       the table in GlobalExceptionHandler.</li>
 * </ul>
 * The schema of ErrorResponse is written by hand (not derived from the record) so it does not depend on how
 * springdoc handles the JSON library of Spring Boot 4.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String ERROR_SCHEMA = "ErrorResponse";

    /** Endpoints that need no access token. Keep in step with the permitAll list in SecurityConfig. */
    static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    @Bean
    public OpenAPI apiDescription() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java Learning App API")
                        .version("v1")
                        .description("REST API of the Java learning app. Every error has the same body "
                                + "(code, message and, for form errors, fieldErrors); a client decides by code, "
                                + "never by the message text. The list of error codes is in the README."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer publicEndpointsAndErrors() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSchemas(ERROR_SCHEMA, errorSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                boolean open = PUBLIC_PATHS.contains(path);
                for (Operation operation : item.readOperations()) {
                    if (open) {
                        // An empty list switches off the global requirement for this operation.
                        operation.setSecurity(List.of());
                    } else {
                        addError(operation, "401",
                                "No usable access token (UNAUTHENTICATED), or the user is deactivated or deleted. "
                                        + "Try /api/v1/auth/refresh once, then send the user to the login.");
                        addError(operation, "403",
                                "Signed in but not allowed (for example ACCESS_DENIED for a wrong role). "
                                        + "The error code says why.");
                    }
                    addError(operation, "429",
                            "Too many requests (RATE_LIMITED). The Retry-After header says how many seconds to wait.");
                    addError(operation, "500", "Unexpected server error (INTERNAL_ERROR), without details.");
                }
            });
        };
    }

    private static void addError(Operation operation, String status, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Schema<?> reference = new Schema<>();
        reference.set$ref("#/components/schemas/" + ERROR_SCHEMA);
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(reference))));
    }

    private static ObjectSchema errorSchema() {
        StringSchema code = new StringSchema();
        code.setDescription("Machine-readable error code; the client decides by this.");
        StringSchema message = new StringSchema();
        message.setDescription("Text for the user (Slovak).");
        ObjectSchema fieldErrors = new ObjectSchema();
        fieldErrors.setDescription("Only for validation errors: field name to message.");
        fieldErrors.setAdditionalProperties(new StringSchema());

        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Uniform error body.");
        schema.addProperty("code", code);
        schema.addProperty("message", message);
        schema.addProperty("fieldErrors", fieldErrors);
        schema.setRequired(List.of("code", "message"));
        return schema;
    }
}
