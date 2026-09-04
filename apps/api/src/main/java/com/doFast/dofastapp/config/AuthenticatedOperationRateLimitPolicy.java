package com.doFast.dofastapp.config;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Set;

final class AuthenticatedOperationRateLimitPolicy {

    static final int DEFAULT_MUTATION_COST = 1;
    static final int MAX_SINGLE_REQUEST_COST = 20;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<HttpMethod> MUTATION_METHODS = Set.of(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE
    );
    private static final List<Rule> AUDITED_RULES = List.of(
            // External provider dispatches and financial operations.
            new Rule(HttpMethod.POST, "/routing/quotes", 20),
            new Rule(HttpMethod.GET, "/routing/quotes/*/mode-estimates", 20),
            new Rule(HttpMethod.POST, "/payments/create-intent", 20),
            new Rule(HttpMethod.POST, "/payments/refunds", 20),
            new Rule(HttpMethod.POST, "/jobs/publications/*/payment-intent", 20),
            new Rule(HttpMethod.POST, "/wallet/payouts/onboarding/refresh", 20),
            new Rule(HttpMethod.POST, "/wallet/payouts/onboarding/link", 20),
            new Rule(HttpMethod.POST, "/wallet/payouts", 20),
            new Rule(HttpMethod.POST, "/admin/payouts/*/retry", 20),

            // Storage-heavy writes and settlement transitions.
            new Rule(HttpMethod.POST, "/admin/payouts/*/fail", 10),
            new Rule(HttpMethod.POST, "/jobs", 10),
            new Rule(HttpMethod.POST, "/jobs/publications", 10),
            new Rule(HttpMethod.POST, "/jobs/publications/*/cancel", 10),
            new Rule(HttpMethod.POST, "/jobs/*/accept", 10),
            new Rule(HttpMethod.POST, "/jobs/*/take", 10),
            new Rule(HttpMethod.POST, "/jobs/*/completion", 10),
            new Rule(HttpMethod.POST, "/jobs/*/confirm", 10),
            new Rule(HttpMethod.POST, "/jobs/*/done", 10),
            new Rule(HttpMethod.POST, "/jobs/*/cancel", 10),
            new Rule(HttpMethod.POST, "/jobs/*/proposals/*/accept", 10),
            new Rule(HttpMethod.POST, "/jobs/*/attachments", 10),
            new Rule(HttpMethod.GET, "/jobs/*/attachments/*/content", 10),
            new Rule(HttpMethod.POST, "/jobs/*/expenses/claims", 10),
            new Rule(HttpMethod.POST, "/jobs/*/cancellation/approve", 10),
            new Rule(HttpMethod.POST, "/admin/disputes/*/resolve", 10),
            new Rule(HttpMethod.POST, "/admin/job-reports/*/enforcement", 10),
            new Rule(HttpMethod.POST, "/admin/job-reports/*/account-enforcement", 10),

            // Query amplification, history and aggregate reads.
            new Rule(HttpMethod.GET, "/jobs/recommended", 5),
            new Rule(HttpMethod.GET, "/jobs/my", 5),
            new Rule(HttpMethod.GET, "/saved-searches/*/results", 5),
            new Rule(HttpMethod.GET, "/jobs/publications/pending", 5),
            new Rule(HttpMethod.GET, "/jobs/*/proposals/*/acceptance-funding", 5),
            new Rule(HttpMethod.GET, "/wallet/transactions", 5),
            new Rule(HttpMethod.GET, "/wallet/payouts", 5),
            new Rule(HttpMethod.GET, "/wallet/payouts/eligibility", 5),
            new Rule(HttpMethod.GET, "/disputes/my", 5),
            new Rule(HttpMethod.GET, "/job-reports/mine", 5),
            new Rule(HttpMethod.GET, "/chat/conversations", 5),
            new Rule(HttpMethod.GET, "/chat/jobs/*/messages", 5),
            new Rule(HttpMethod.GET, "/admin/disputes/*/messages", 5),
            new Rule(HttpMethod.GET, "/admin/finance/reconciliation", 5)
    );

    private AuthenticatedOperationRateLimitPolicy() {}

    static int costUnits(String method, String path) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        if (isPublic(httpMethod, path)) {
            return 0;
        }
        for (Rule rule : AUDITED_RULES) {
            if (rule.matches(httpMethod, path)) {
                return rule.costUnits();
            }
        }
        return MUTATION_METHODS.contains(httpMethod) ? DEFAULT_MUTATION_COST : 0;
    }

    static List<Rule> auditedRules() {
        return AUDITED_RULES;
    }

    private static boolean isPublic(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method)) {
            return matchesAny(HttpAuthorizationPolicy.PUBLIC_POST_PATHS, path);
        }
        if (HttpMethod.GET.equals(method)) {
            return matchesAny(HttpAuthorizationPolicy.PUBLIC_GET_PATHS, path);
        }
        return false;
    }

    private static boolean matchesAny(String[] patterns, String path) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    record Rule(HttpMethod method, String pathPattern, int costUnits) {
        Rule {
            if (method == null || pathPattern == null || pathPattern.isBlank()
                    || costUnits <= DEFAULT_MUTATION_COST || costUnits > MAX_SINGLE_REQUEST_COST) {
                throw new IllegalArgumentException("Invalid authenticated operation rate-limit rule");
            }
        }

        boolean matches(HttpMethod candidateMethod, String path) {
            return method.equals(candidateMethod) && PATH_MATCHER.match(pathPattern, path);
        }
    }
}
