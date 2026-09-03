package com.doFast.dofastapp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
    private final PublicJobDiscoveryRateLimitFilter publicJobDiscoveryRateLimitFilter;
    private final AuthenticatedRoutingRateLimitFilter authenticatedRoutingRateLimitFilter;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${dofast.security.public-auth-rate-limit.max-requests:30}") int maxRequests,
            @Value("${dofast.security.public-auth-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${dofast.security.public-auth-rate-limit.max-entries:10000}") int maxEntries,
            @Value("${dofast.security.public-auth-rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${dofast.security.public-job-discovery-rate-limit.max-requests:120}") int discoveryMaxRequests,
            @Value("${dofast.security.public-job-discovery-rate-limit.window-seconds:60}") long discoveryWindowSeconds,
            @Value("${dofast.security.public-job-discovery-rate-limit.max-entries:10000}") int discoveryMaxEntries,
            @Value("${dofast.security.public-job-discovery-rate-limit.trust-forwarded-for:false}") boolean discoveryTrustForwardedFor,
            @Value("${dofast.security.authenticated-routing-rate-limit.max-provider-calls:60}") int routingMaxProviderCalls,
            @Value("${dofast.security.authenticated-routing-rate-limit.window-seconds:60}") long routingWindowSeconds,
            @Value("${dofast.security.authenticated-routing-rate-limit.max-entries:10000}") int routingMaxEntries
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.publicAuthRateLimitFilter = new PublicAuthRateLimitFilter(
                maxRequests,
                windowSeconds,
                maxEntries,
                trustForwardedFor
        );
        this.publicJobDiscoveryRateLimitFilter = new PublicJobDiscoveryRateLimitFilter(
                discoveryMaxRequests,
                discoveryWindowSeconds,
                discoveryMaxEntries,
                discoveryTrustForwardedFor
        );
        this.authenticatedRoutingRateLimitFilter = new AuthenticatedRoutingRateLimitFilter(
                routingMaxProviderCalls,
                routingWindowSeconds,
                routingMaxEntries
        );
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
                        .requestMatchers(HttpMethod.POST, HttpAuthorizationPolicy.PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, HttpAuthorizationPolicy.PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpAuthorizationPolicy.PUBLIC_TRANSPORT_PATHS).permitAll()
                        .requestMatchers(HttpAuthorizationPolicy.PUBLIC_HEALTH_PATHS).permitAll()
                        .requestMatchers(HttpAuthorizationPolicy.ADMIN_PATHS).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(publicJobDiscoveryRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(publicAuthRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authenticatedRoutingRateLimitFilter, JwtAuthFilter.class);

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
