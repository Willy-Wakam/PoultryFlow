package com.poultryflow.identity.security;

import com.poultryflow.shared.api.ApiContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
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
            AuthenticationProblemEntryPoint authenticationProblemEntryPoint)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ApiContract.BUSINESS_API_BASE_PATH + "/**").authenticated()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        ApiContract.BUSINESS_API_BASE_PATH + "/**"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationProblemEntryPoint)
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }
}
