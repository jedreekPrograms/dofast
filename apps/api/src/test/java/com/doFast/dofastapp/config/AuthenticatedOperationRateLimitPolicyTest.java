package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedOperationRateLimitPolicyTest {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<RequestMethod> MUTATIONS = Set.of(
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE
    );

    @Test
    void everyPrivateMutationConsumesTheSharedAccountBudget() throws Exception {
        List<Endpoint> endpoints = controllerEndpoints();
        assertTrue(endpoints.size() >= 130, "Controller scan returned an unexpectedly small HTTP surface");

        for (Endpoint endpoint : endpoints) {
            if (MUTATIONS.contains(endpoint.method()) && !isPublic(endpoint)) {
                assertTrue(
                        costUnits(endpoint) > 0,
                        () -> endpoint.key() + " bypasses the authenticated operation budget"
                );
            }
        }
    }

    @Test
    void everyWeightedRuleResolvesToOneUniqueControllerEndpoint() throws Exception {
        List<Endpoint> endpoints = controllerEndpoints();
        Set<String> matchedEndpoints = new HashSet<>();

        assertEquals(41, AuthenticatedOperationRateLimitPolicy.auditedRules().size(),
                "Weighted operation inventory changed; update its tests and runbook");
        for (AuthenticatedOperationRateLimitPolicy.Rule rule
                : AuthenticatedOperationRateLimitPolicy.auditedRules()) {
            List<Endpoint> matches = endpoints.stream()
                    .filter(endpoint -> rule.method().equals(HttpMethod.valueOf(endpoint.method().name())))
                    .filter(endpoint -> PATH_MATCHER.match(rule.pathPattern(), endpoint.path()))
                    .toList();
            assertEquals(1, matches.size(),
                    () -> rule.method() + " " + rule.pathPattern() + " must resolve to exactly one endpoint");
            assertTrue(matchedEndpoints.add(matches.getFirst().key()),
                    () -> matches.getFirst().key() + " is covered by more than one weighted rule");
            assertEquals(rule.costUnits(), costUnits(matches.getFirst()),
                    () -> matches.getFirst().key() + " does not use its audited weight");
        }
    }

    @Test
    void anonymousHttpSurfaceNeverConsumesAuthenticatedBudget() throws Exception {
        for (Endpoint endpoint : controllerEndpoints()) {
            if (isPublic(endpoint)) {
                assertEquals(0, costUnits(endpoint),
                        () -> endpoint.key() + " belongs to a dedicated public limiter");
            }
        }
    }

    @Test
    void unknownPrivateMutationFailsIntoBaselineBudgetWhileOrdinaryReadDoesNot() {
        assertEquals(AuthenticatedOperationRateLimitPolicy.DEFAULT_MUTATION_COST,
                AuthenticatedOperationRateLimitPolicy.costUnits("PATCH", "/future/private-resource/1"));
        assertEquals(0, AuthenticatedOperationRateLimitPolicy.costUnits("GET", "/future/private-resource/1"));
    }

    private int costUnits(Endpoint endpoint) {
        return AuthenticatedOperationRateLimitPolicy.costUnits(endpoint.method().name(), endpoint.path());
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
                            endpoints.add(new Endpoint(requestMethod, join(basePath, methodPath)));
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

    private record Endpoint(RequestMethod method, String path) {
        String key() {
            return method.name() + " " + path;
        }
    }
}
