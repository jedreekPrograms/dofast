package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAuthorizationPolicyTest {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> AUTHENTICATED_ACTORLESS_READS = Set.of(
            "GET /payments/platform-fee-policy"
    );

    @Test
    void everyHttpEndpointHasAnExplicitAuthorizationBoundary() throws Exception {
        List<Endpoint> endpoints = controllerEndpoints();

        assertTrue(endpoints.size() >= 130, "Controller scan returned an unexpectedly small HTTP surface");
        assertEquals(endpoints.size(), endpointKeys(endpoints).size(), "Duplicate HTTP method/path mapping detected");

        for (Endpoint endpoint : endpoints) {
            if (isPublic(endpoint)) {
                continue;
            }

            boolean adminEndpoint = matchesAny(HttpAuthorizationPolicy.ADMIN_PATHS, endpoint.path());
            boolean actorlessRead = AUTHENTICATED_ACTORLESS_READS.contains(endpoint.key());
            assertTrue(
                    adminEndpoint || endpoint.hasAuthenticatedUser() || actorlessRead,
                    () -> endpoint.key() + " does not carry an authenticated User into its controller boundary"
            );

            if (adminEndpoint) {
                assertTrue(
                        endpoint.hasAuthenticatedUser(),
                        () -> endpoint.key() + " must pass the admin principal to its service boundary"
                );
            }
        }
    }

    @Test
    void anonymousAllowlistContainsOnlyExistingControllerEndpoints() throws Exception {
        List<Endpoint> endpoints = controllerEndpoints();
        Set<String> matchedEndpoints = new HashSet<>();

        assertPatternsResolve(
                HttpAuthorizationPolicy.PUBLIC_POST_PATHS,
                RequestMethod.POST,
                endpoints,
                matchedEndpoints
        );
        assertPatternsResolve(HttpAuthorizationPolicy.PUBLIC_GET_PATHS, RequestMethod.GET, endpoints, matchedEndpoints);

        assertEquals(17, matchedEndpoints.size(), "Anonymous HTTP surface changed; update the authorization matrix");
        assertFalse(
                matchedEndpoints.stream().anyMatch(key -> key.startsWith("DELETE ") || key.startsWith("PATCH ")
                        || key.startsWith("PUT ")),
                "Anonymous HTTP surface must not expose direct mutations"
        );
    }

    private void assertPatternsResolve(
            String[] patterns,
            RequestMethod method,
            List<Endpoint> endpoints,
            Set<String> matchedEndpoints
    ) {
        for (String pattern : patterns) {
            List<Endpoint> matches = endpoints.stream()
                    .filter(endpoint -> endpoint.method() == method)
                    .filter(endpoint -> PATH_MATCHER.match(pattern, endpoint.path()))
                    .toList();
            assertEquals(1, matches.size(), () -> method + " " + pattern + " must resolve to exactly one endpoint");
            matchedEndpoints.add(matches.getFirst().key());
        }
    }

    private boolean isPublic(Endpoint endpoint) {
        return switch (endpoint.method()) {
            case GET -> matchesAny(HttpAuthorizationPolicy.PUBLIC_GET_PATHS, endpoint.path());
            case POST -> matchesAny(HttpAuthorizationPolicy.PUBLIC_POST_PATHS, endpoint.path());
            default -> false;
        };
    }

    private boolean matchesAny(String[] patterns, String path) {
        return Arrays.stream(patterns).anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Set<String> endpointKeys(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::key).collect(java.util.stream.Collectors.toSet());
    }

    private List<Endpoint> controllerEndpoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Endpoint> endpoints = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.doFast.dofastapp")) {
            Class<?> controller = ClassUtils.forName(
                    candidate.getBeanClassName(),
                    ClassUtils.getDefaultClassLoader()
            );
            List<String> basePaths = mappingPaths(
                    AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class)
            );
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                assertTrue(mapping.method().length > 0, () -> method + " must declare an HTTP method");
                for (String basePath : basePaths) {
                    for (String methodPath : mappingPaths(mapping)) {
                        for (RequestMethod requestMethod : mapping.method()) {
                            endpoints.add(new Endpoint(requestMethod, join(basePath, methodPath), method));
                        }
                    }
                }
            }
        }

        endpoints.sort(Comparator.comparing(Endpoint::key));
        return List.copyOf(endpoints);
    }

    private List<String> mappingPaths(RequestMapping mapping) {
        if (mapping == null) {
            return List.of("");
        }
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return paths.length == 0 ? List.of("") : List.of(paths);
    }

    private String join(String basePath, String methodPath) {
        String joined = ("/" + basePath + "/" + methodPath).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            return joined.substring(0, joined.length() - 1);
        }
        return joined;
    }

    private record Endpoint(RequestMethod method, String path, Method handler) {
        String key() {
            return method.name() + " " + path;
        }

        boolean hasAuthenticatedUser() {
            return Arrays.stream(handler.getParameters())
                    .anyMatch(Endpoint::isAuthenticatedUser);
        }

        private static boolean isAuthenticatedUser(Parameter parameter) {
            return parameter.getType() == User.class
                    && parameter.isAnnotationPresent(AuthenticationPrincipal.class);
        }
    }
}
