package com.poultryflow.shared.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {
        com.poultryflow.PoultryFlowApplication.class,
        OpenApiContractIntegrationTests.OpenApiTestConfiguration.class
})
class OpenApiContractIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void publishesApiMetadataAndReusableContractComponents() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("PoultryFlow API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.type").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.title").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.detail").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.instance").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.required")
                        .value(containsInAnyOrder("status", "code")))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.violations.items['$ref']")
                        .value("#/components/schemas/ValidationViolation"))
                .andExpect(jsonPath("$.components.schemas.ValidationViolation.properties.field").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationViolation.properties.message").exists())
                .andExpect(jsonPath("$.components.parameters.IdempotencyKey.name")
                        .value(ApiContract.IDEMPOTENCY_KEY_HEADER))
                .andExpect(jsonPath("$.components.parameters.IdempotencyKey.in").value("header"))
                .andExpect(jsonPath("$.components.parameters.IdempotencyKey.description")
                        .value(containsString("UUID")))
                .andExpect(jsonPath(
                                "$.components.responses.ProblemResponse.content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type")
                        .value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat")
                        .value("JWT"))
                .andExpect(jsonPath(
                                "$.paths['/api/v1/test/openapi'].get.security[0].BearerAuth")
                        .isArray());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenApiTestConfiguration {

        @Bean
        OpenApiTestController openApiTestController() {
            return new OpenApiTestController();
        }
    }

    @RestController
    static class OpenApiTestController {

        @GetMapping("/api/v1/test/openapi")
        void documentedBusinessOperation() {
        }
    }
}
