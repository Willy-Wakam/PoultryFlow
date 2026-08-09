package com.poultryflow.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poultryflow.PoultryFlowApplication;
import com.poultryflow.shared.api.ApiContract;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {
        PoultryFlowApplication.class,
        SecurityIntegrationTests.SecurityTestConfiguration.class
})
class SecurityIntegrationTests {

    private static final String TEST_API_PATH = "/api/v1/test/security";
    private static final String UNLISTED_TEST_PATH = "/test/security-fallback";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Environment environment;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void keepsDocumentedInfrastructureEndpointsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsSafeProblemDetailWhenAccessTokenIsMissing() throws Exception {
        mockMvc.perform(get(TEST_API_PATH).accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(ApiContract.AUTHENTICATION_REQUIRED_CODE))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void allowsAuthenticatedJwtWithoutCreatingAnHttpSession() throws Exception {
        MvcResult result = mockMvc.perform(get(TEST_API_PATH).with(jwt().jwt(jwt -> jwt
                                .issuer("http://localhost:8081/realms/poultryflow")
                                .audience(java.util.List.of("poultryflow-api")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authenticated"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void returnsSafeProblemDetailForUnauthenticatedUnlistedRoute() throws Exception {
        mockMvc.perform(get(UNLISTED_TEST_PATH).accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(ApiContract.AUTHENTICATION_REQUIRED_CODE))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void allowsAuthenticatedJwtOnUnlistedRouteWithoutRoleMapping() throws Exception {
        mockMvc.perform(get(UNLISTED_TEST_PATH).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authenticated"));
    }

    @Test
    void configuresIssuerJwkSetAndAudienceValidation() {
        assertThat(environment.getProperty(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .isEqualTo("http://localhost:8081/realms/poultryflow");
        assertThat(environment.getProperty(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                .isEqualTo("http://localhost:8081/realms/poultryflow/protocol/openid-connect/certs");
        assertThat(environment.getProperty(
                        "spring.security.oauth2.resourceserver.jwt.audiences"))
                .isEqualTo("poultryflow-api");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        SecurityTestController securityTestController() {
            return new SecurityTestController();
        }
    }

    @RestController
    static class SecurityTestController {

        @GetMapping(TEST_API_PATH)
        Map<String, String> authenticated() {
            return Map.of("status", "authenticated");
        }

        @GetMapping(UNLISTED_TEST_PATH)
        Map<String, String> authenticatedFallback() {
            return Map.of("status", "authenticated");
        }
    }
}
