package com.doFast.dofastapp.config;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDtoBoundaryPolicyTest {

    private static final String PROJECT_PACKAGE = "com.doFast.dofastapp";
    private static final String STRIPE_WEBHOOK_HANDLER =
            "com.doFast.dofastapp.payment.webhook.StripeWebhookController#handle";

    private static final Set<String> SERVER_CONTROLLED_FIELDS = Set.of(
            "id",
            "userId",
            "ownerId",
            "createdBy",
            "createdById",
            "takenBy",
            "takenById",
            "assignedTo",
            "requesterId",
            "workerId",
            "proposerId",
            "reviewerId",
            "revieweeId",
            "role",
            "roles",
            "authorities",
            "balance",
            "walletBalance",
            "emailVerified",
            "passwordHash",
            "authVersion",
            "version",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "acceptedAt",
            "completedAt",
            "cancelledAt",
            "stripeCustomerId",
            "stripeAccountId"
    );

    private static final Map<String, Set<String>> AUDITED_REQUEST_FIELDS = Map.ofEntries(
            fields("chat.dto.ChatMessageRequest", "jobId", "content", "clientMessageId"),
            fields("chat.dto.MarkChatReadRequest", "lastMessageId"),
            fields("chat.dto.SendChatMessageRequest", "content", "clientMessageId"),
            fields("dispute.dto.CreateDisputeRequest", "jobId", "reason", "description"),
            fields("dispute.dto.ResolveDisputeRequest", "resolution", "note", "approvedExpenseAmount"),
            fields("job.cancellation.dto.CreateJobCancellationRequest", "reason"),
            fields("job.dto.JobRequest", "title", "description", "price", "expenseBudget", "categoryId",
                    "routeQuoteId", "location", "assignmentMode", "priceNegotiationEnabled"),
            fields("job.expense.CreateJobExpenseClaimRequest", "amount", "attachmentId"),
            fields("job.proposal.CreateJobProposalRequest", "amount", "message"),
            fields("job.publication.dto.CreateJobPublicationRequest", "requestId", "job"),
            fields("job.report.EnforceJobReportAccountRequest", "action", "reason"),
            fields("job.report.EnforceJobReportRequest", "action", "reason"),
            fields("job.report.JobReportRequest", "reason", "details"),
            fields("job.report.ModerateJobReportRequest", "status", "note"),
            fields("job.search.SavedSearchRequest", "name", "query", "categorySlug", "minPrice", "maxPrice",
                    "latitude", "longitude", "radiusKm", "alertsEnabled"),
            fields("location.routing.dto.RoutePointRequest", "latitude", "longitude", "publicLabel",
                    "privateLabel", "placeId"),
            fields("location.routing.dto.RouteQuoteRequest", "origin", "stops", "destination"),
            fields("location.tracking.dto.LiveLocationUpdateRequest", "latitude", "longitude", "accuracyMeters",
                    "headingDegrees", "speedMetersPerSecond", "capturedAt"),
            fields("notification.dto.UpdateNotificationPreferencesRequest", "mutedTypes"),
            fields("payment.dto.CreatePaymentIntentRequest", "amount", "requestId"),
            fields("payment.refund.dto.CreateStripeRefundRequest", "requestId", "paymentIntentId", "amount"),
            fields("payout.dto.AdminPayoutFailureRequest", "reason"),
            fields("payout.dto.CreatePayoutRequest", "amount", "requestId"),
            fields("review.dto.ReviewRequest", "jobId", "rating", "comment"),
            fields("user.auth.apple.AppleLoginRequest", "challengeId", "code", "state", "nonce", "firstName",
                    "lastName"),
            fields("user.dto.ChangePasswordRequest", "currentPassword", "newPassword"),
            fields("user.dto.ForgotPasswordRequest", "email"),
            fields("user.dto.GoogleLoginRequest", "credential"),
            fields("user.dto.LoginRequest", "email", "password"),
            fields("user.dto.ResendEmailVerificationRequest", "email"),
            fields("user.dto.ResetPasswordRequest", "token", "newPassword"),
            fields("user.dto.UpdateProfileRequest", "nickname", "bio", "publicLocation"),
            fields("user.dto.UpdateUserServiceAreaRequest", "latitude", "longitude", "radiusKm"),
            fields("user.dto.UpdateUserServiceCategoriesRequest", "categoryIds"),
            fields("user.dto.UpdateUserStatusRequest", "status", "reason"),
            fields("user.dto.UserRequest", "email", "nickname", "password"),
            fields("user.dto.VerifyEmailRequest", "token"),
            fields("verification.dto.AdminVerificationDecisionRequest", "decision", "reason")
    );

    @Test
    void everyJsonAndStompPayloadUsesAnAuditedValidatedDto() throws Exception {
        List<TransportInput> inputs = transportInputs();

        assertEquals(39, inputs.size(), "Bound payload surface changed; audit the new transport input");
        assertEquals(1, inputs.stream().filter(TransportInput::isSignedStripeWebhook).count());

        for (TransportInput input : inputs) {
            if (input.isSignedStripeWebhook()) {
                assertEquals(String.class, input.parameter().getType());
                continue;
            }

            assertTrue(
                    input.parameter().isAnnotationPresent(Valid.class),
                    () -> input.description() + " must validate its request DTO"
            );
            assertTrue(
                    input.parameter().getType().getPackageName().startsWith(PROJECT_PACKAGE),
                    () -> input.description() + " must not bind an untyped or framework object"
            );
            assertFalse(
                    isPersistenceType(input.parameter().getType()),
                    () -> input.description() + " must not bind a persistence model"
            );
        }

        Set<Class<?>> requestTypes = discoverRequestTypes(inputs);
        Set<String> discoveredNames = requestTypes.stream()
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(AUDITED_REQUEST_FIELDS.keySet(), discoveredNames, "Request DTO type allowlist changed");

        for (Class<?> requestType : requestTypes) {
            Set<String> actualFields = declaredWireFields(requestType);
            assertEquals(
                    AUDITED_REQUEST_FIELDS.get(requestType.getName()),
                    actualFields,
                    () -> requestType.getName() + " request field allowlist changed"
            );
            assertTrue(
                    actualFields.stream().noneMatch(SERVER_CONTROLLED_FIELDS::contains),
                    () -> requestType.getName() + " exposes a server-controlled field"
            );
            assertNoJacksonBindingEscapeHatches(requestType);
        }
    }

    @Test
    void unknownJsonFieldsAreRejectedInsteadOfSilentlyIgnored() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Object configured = sources.stream()
                .map(source -> source.getProperty("spring.jackson.deserialization.fail-on-unknown-properties"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertNotNull(configured, "Strict unknown-field handling must be configured explicitly");
        assertEquals("true", configured.toString());
    }

    @Test
    void controllerResponsesNeverExposePersistenceModels() throws Exception {
        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                assertNoPersistenceType(method.getGenericReturnType(), controller.getName() + "#" + method.getName());
            }
        }
    }

    private List<TransportInput> transportInputs() throws Exception {
        List<TransportInput> inputs = new ArrayList<>();
        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)
                            || parameter.isAnnotationPresent(Payload.class)) {
                        inputs.add(new TransportInput(method, parameter));
                    }
                }
            }
        }
        return List.copyOf(inputs);
    }

    private Set<Class<?>> controllerClasses() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> controllers = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents(PROJECT_PACKAGE)) {
            controllers.add(ClassUtils.forName(
                    candidate.getBeanClassName(),
                    ClassUtils.getDefaultClassLoader()
            ));
        }
        return Set.copyOf(controllers);
    }

    private Set<Class<?>> discoverRequestTypes(List<TransportInput> inputs) {
        Set<Class<?>> discovered = new LinkedHashSet<>();
        ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        inputs.stream()
                .filter(input -> !input.isSignedStripeWebhook())
                .map(input -> input.parameter().getType())
                .forEach(type -> {
                    if (discovered.add(type)) pending.add(type);
                });

        while (!pending.isEmpty()) {
            Class<?> requestType = pending.removeFirst();
            assertFalse(isPersistenceType(requestType), () -> requestType.getName() + " is a persistence model");
            for (Field field : requestType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                Set<Class<?>> nestedRequestTypes = classesIn(field.getGenericType()).stream()
                        .filter(valueType -> valueType.getPackageName().startsWith(PROJECT_PACKAGE))
                        .filter(valueType -> !valueType.isEnum())
                        .collect(java.util.stream.Collectors.toSet());
                if (!nestedRequestTypes.isEmpty()) {
                    assertTrue(
                            field.isAnnotationPresent(Valid.class)
                                    || hasValidationCascade(field.getAnnotatedType()),
                            () -> requestType.getName() + "." + field.getName()
                                    + " must cascade validation to its nested request DTO"
                    );
                }
                for (Class<?> valueType : nestedRequestTypes) {
                    assertTrue(
                            valueType.getSimpleName().endsWith("Request"),
                            () -> requestType.getName() + "." + field.getName()
                                    + " nests unaudited domain type " + valueType.getName()
                    );
                    if (discovered.add(valueType)) pending.add(valueType);
                }
            }
        }
        return Set.copyOf(discovered);
    }

    private boolean hasValidationCascade(AnnotatedType type) {
        if (type.isAnnotationPresent(Valid.class)) return true;
        if (type instanceof AnnotatedParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getAnnotatedActualTypeArguments())
                    .anyMatch(this::hasValidationCascade);
        }
        if (type instanceof AnnotatedArrayType arrayType) {
            return hasValidationCascade(arrayType.getAnnotatedGenericComponentType());
        }
        if (type instanceof AnnotatedWildcardType wildcardType) {
            return Arrays.stream(wildcardType.getAnnotatedUpperBounds()).anyMatch(this::hasValidationCascade)
                    || Arrays.stream(wildcardType.getAnnotatedLowerBounds()).anyMatch(this::hasValidationCascade);
        }
        return false;
    }

    private Set<Class<?>> classesIn(Type type) {
        Set<Class<?>> classes = new HashSet<>();
        collectClasses(type, classes);
        return classes;
    }

    private void collectClasses(Type type, Set<Class<?>> classes) {
        if (type instanceof Class<?> typeClass) {
            if (typeClass.isArray()) collectClasses(typeClass.getComponentType(), classes);
            else classes.add(typeClass);
            return;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            collectClasses(parameterizedType.getRawType(), classes);
            Arrays.stream(parameterizedType.getActualTypeArguments())
                    .forEach(argument -> collectClasses(argument, classes));
            return;
        }
        if (type instanceof GenericArrayType arrayType) {
            collectClasses(arrayType.getGenericComponentType(), classes);
            return;
        }
        if (type instanceof WildcardType wildcardType) {
            Arrays.stream(wildcardType.getUpperBounds()).forEach(bound -> collectClasses(bound, classes));
            Arrays.stream(wildcardType.getLowerBounds()).forEach(bound -> collectClasses(bound, classes));
        }
    }

    private Set<String> declaredWireFields(Class<?> requestType) {
        return Arrays.stream(requestType.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void assertNoJacksonBindingEscapeHatches(Class<?> requestType) {
        assertNoJacksonAnnotations(requestType, requestType);
        Arrays.stream(requestType.getDeclaredFields()).forEach(field -> assertNoJacksonAnnotations(field, requestType));
        Arrays.stream(requestType.getDeclaredMethods())
                .forEach(method -> assertNoJacksonAnnotations(method, requestType));
        Arrays.stream(requestType.getDeclaredConstructors()).forEach(constructor -> {
            assertNoJacksonAnnotations(constructor, requestType);
            Arrays.stream(constructor.getParameters())
                    .forEach(parameter -> assertNoJacksonAnnotations(parameter, requestType));
        });
        if (requestType.isRecord()) {
            Arrays.stream(requestType.getRecordComponents())
                    .forEach(component -> assertNoJacksonAnnotations(component, requestType));
        }
    }

    private void assertNoJacksonAnnotations(AnnotatedElement element, Class<?> requestType) {
        List<String> annotations = Arrays.stream(element.getAnnotations())
                .map(annotation -> annotation.annotationType().getName())
                .filter(name -> name.contains(".jackson."))
                .toList();
        assertTrue(
                annotations.isEmpty(),
                () -> requestType.getName() + " must not add hidden JSON aliases/ignore/any-setter escape hatches: "
                        + annotations
        );
    }

    private void assertNoPersistenceType(Type returnType, String handler) {
        Set<Class<?>> visited = new HashSet<>();
        ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        classesIn(returnType).stream()
                .filter(this::isProjectType)
                .forEach(pending::add);

        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!visited.add(type) || type.isEnum()) continue;
            assertFalse(isPersistenceType(type), () -> handler + " exposes persistence type " + type.getName());
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                classesIn(field.getGenericType()).stream()
                        .filter(this::isProjectType)
                        .forEach(pending::add);
            }
        }
    }

    private boolean isProjectType(Class<?> type) {
        return type.getPackageName().startsWith(PROJECT_PACKAGE);
    }

    private static boolean isPersistenceType(Class<?> type) {
        return type.isAnnotationPresent(Entity.class)
                || type.isAnnotationPresent(MappedSuperclass.class)
                || type.isAnnotationPresent(Embeddable.class);
    }

    private static Map.Entry<String, Set<String>> fields(String relativeClassName, String... fieldNames) {
        return Map.entry(PROJECT_PACKAGE + "." + relativeClassName, Set.of(fieldNames));
    }

    private record TransportInput(Method handler, Parameter parameter) {
        boolean isSignedStripeWebhook() {
            return description().equals(STRIPE_WEBHOOK_HANDLER)
                    && parameter.isAnnotationPresent(RequestBody.class);
        }

        String description() {
            return handler.getDeclaringClass().getName() + "#" + handler.getName();
        }
    }
}
