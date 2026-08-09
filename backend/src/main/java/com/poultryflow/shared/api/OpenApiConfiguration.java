package com.poultryflow.shared.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI poultryFlowOpenApi() {
        Components components = new Components()
                .addSchemas(ApiContract.VALIDATION_VIOLATION_SCHEMA, validationViolationSchema())
                .addSchemas(ApiContract.PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
                .addParameters(ApiContract.IDEMPOTENCY_KEY_PARAMETER, idempotencyKeyParameter())
                .addResponses(ApiContract.PROBLEM_RESPONSE, problemResponse())
                .addSecuritySchemes(ApiContract.BEARER_AUTH_SCHEME, bearerAuthScheme());

        return new OpenAPI()
                .info(new Info()
                        .title("PoultryFlow API")
                        .version("v1")
                        .description("REST API contract for PoultryFlow farm management operations."))
                .components(components);
    }

    @Bean
    OpenApiCustomizer authenticatedBusinessApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith(ApiContract.BUSINESS_API_BASE_PATH + "/")) {
                    pathItem.readOperations().forEach(operation -> operation.addSecurityItem(
                            new SecurityRequirement().addList(ApiContract.BEARER_AUTH_SCHEME)));
                }
            });
        };
    }

    private Schema<?> validationViolationSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("field", new StringSchema()
                .description("Request field or object that failed validation."));
        schema.addProperty("message", new StringSchema()
                .description("Client-safe explanation of the validation failure."));
        schema.setRequired(List.of("field", "message"));
        return schema;
    }

    private Schema<?> problemDetailSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema()
                .format("uri")
                .description("URI reference identifying the problem type."));
        schema.addProperty("title", new StringSchema()
                .description("Short, human-readable summary of the problem type."));
        schema.addProperty("status", new IntegerSchema()
                .format("int32")
                .description("HTTP status code for this occurrence."));
        schema.addProperty("detail", new StringSchema()
                .description("Client-safe explanation specific to this occurrence."));
        schema.addProperty("instance", new StringSchema()
                .format("uri")
                .description("URI reference identifying this problem occurrence."));
        schema.addProperty("code", new StringSchema()
                .description("Stable PoultryFlow machine-readable error code."));
        schema.addProperty("violations", new ArraySchema()
                .description("Structured validation failures when applicable.")
                .items(new Schema<>()
                        .$ref("#/components/schemas/"
                                + ApiContract.VALIDATION_VIOLATION_SCHEMA)));
        schema.setRequired(List.of("status", "code"));
        return schema;
    }

    private Parameter idempotencyKeyParameter() {
        return new HeaderParameter()
                .name(ApiContract.IDEMPOTENCY_KEY_HEADER)
                .required(false)
                .description("Opaque client-generated operation identifier. PoultryFlow clients "
                        + "normally use a UUID and reuse it only when retrying the same logical "
                        + "mutation.")
                .schema(new StringSchema());
    }

    private ApiResponse problemResponse() {
        io.swagger.v3.oas.models.media.MediaType problemMediaType =
                new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().$ref(ApiContract.PROBLEM_DETAIL_SCHEMA_REF));

        return new ApiResponse()
                .description("Request failed with a PoultryFlow problem detail.")
                .content(new Content()
                        .addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE, problemMediaType));
    }

    private SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Keycloak access token for the PoultryFlow API audience.");
    }
}
