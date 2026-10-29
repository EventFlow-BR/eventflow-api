package br.com.eventflow.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure
    ) throws Exception {

        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, cookieName);

        CookieCsrfTokenRepository csrfTokenRepository =
                new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
        );

        http
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login"
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
                                "/actuator/prometheus"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/*/cancel"
                        ).hasRole("ORGANIZER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events"
                        ).hasRole("ORGANIZER")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/events/*"
                        ).hasRole("ORGANIZER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/*/publish"
                        ).hasRole("ORGANIZER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/*/registrations"
                        ).hasRole("PARTICIPANT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/registrations/*/payment"
                        ).hasRole("PARTICIPANT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/registrations/*/cancel"
                        ).hasRole("PARTICIPANT")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
