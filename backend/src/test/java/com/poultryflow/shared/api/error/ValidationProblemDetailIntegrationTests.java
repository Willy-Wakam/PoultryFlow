package com.poultryflow.shared.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poultryflow.PoultryFlowApplication;
import com.poultryflow.shared.api.ApiContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {
        PoultryFlowApplication.class,
        ValidationProblemDetailIntegrationTests.ValidationTestConfiguration.class
})
class ValidationProblemDetailIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void returnsDeterministicProblemDetailForInvalidRequest() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:poultryflow:problem:validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("One or more request fields are invalid."))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.code").value(ApiContract.VALIDATION_FAILED_CODE))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").value("is required"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void addsFallbackCodeToInheritedSpringMvcProblemDetail() throws Exception {
        mockMvc.perform(get("/test/validation")
                        .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.code").value(ApiContract.HTTP_ERROR_CODE))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationTestConfiguration {

        @Bean
        ValidationTestController validationTestController() {
            return new ValidationTestController();
        }
    }

    @RestController
    static class ValidationTestController {

        @PostMapping(path = "/test/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody ValidationRequest request) {
        }
    }

    record ValidationRequest(@NotBlank(message = "is required") String name) {
    }
}
