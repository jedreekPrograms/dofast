package com.doFast.dofastapp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final PublicAuthRateLimitFilter publicAuthRateLimitFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, PublicAuthRateLimitFilter publicAuthRateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.publicAuthRateLimitFilter = publicAuthRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/users",
                                "/users/login",
                                "/users/login/google",
                                "/users/login/apple",
                                "/users/login/apple/challenge",
                                "/users/session/refresh",
                                "/users/session/logout",
                                "/users/password/forgot",
                                "/users/password/reset",
                                "/users/email-verification/resend",
                                "/users/email-verification/verify"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs", "/jobs/nearby", "/job-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/*/profile", "/reviews/users/*").permitAll()
                        .requestMatchers("/ws", "/ws/**", "/ws-sockjs/**").permitAll()
                        .requestMatchers("/webhooks/stripe").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(publicAuthRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeSecurityError(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\"}");
    }
}
