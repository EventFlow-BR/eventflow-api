package br.com.eventflow.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${app.auth.cookie-name}") String cookieName
    ) throws Exception {

        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, cookieName);

        http
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Logout currently only clears the authentication cookie.
                // It is temporarily excluded from CSRF protection until the final
                // CSRF strategy for authenticated state-changing endpoints is implemented.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout"
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events"
                        ).hasRole("ORGANIZER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
