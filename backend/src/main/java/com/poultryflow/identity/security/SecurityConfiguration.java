package com.poultryflow.identity.security;

import com.poultryflow.shared.api.ApiContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProblemEntryPoint authenticationProblemEntryPoint,
            AuthorizationDeniedProblemHandler authorizationDeniedProblemHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ApiContract.BUSINESS_API_BASE_PATH + "/**").authenticated()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        ApiContract.BUSINESS_API_BASE_PATH + "/**"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(authorizationDeniedProblemHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationProblemEntryPoint)
                        .accessDeniedHandler(authorizationDeniedProblemHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            KeycloakClientRoleAuthoritiesConverter authoritiesConverter) {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }
}
