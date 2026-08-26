package com.example.hotelmanagement.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Captures successful service-layer updates/deletes and explicitly marked state mutations.
 * It records method inputs and return values as the before/after audit snapshots; serializers
 * in {@link AuditLogService} redact credentials and tokens before persistence.
 */
@Aspect
@Component
public class ServiceMutationAuditAspect {

    private final AuditLogService auditLogService;

    public ServiceMutationAuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("execution(public * com.example.hotelmanagement.services..*.update*(..))"
            + " || execution(public * com.example.hotelmanagement.services..*.delete*(..))"
            + " || @annotation(com.example.hotelmanagement.audit.AuditMutation)")
    public Object auditMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AuditMutation mutation = AnnotationUtils.findAnnotation(method, AuditMutation.class);
        String methodName = method.getName();

        String entityType = mutation == null
                ? deriveEntityType(joinPoint.getTarget().getClass().getSimpleName())
                : mutation.entityType();
        String action = mutation == null
                ? entityType.toUpperCase(Locale.ROOT) + "_" + toUpperSnakeCase(methodName)
                : mutation.action();
        Object[] arguments = joinPoint.getArgs();

        auditLogService.record(
                action,
                entityType,
                argumentAt(arguments, mutation == null ? -1 : mutation.entityIdArgumentIndex()),
                argumentAt(arguments, mutation == null ? -1 : mutation.actorUserIdArgumentIndex()),
                namedArguments(method, arguments),
                result == null ? Map.of("deleted", true) : result
        );
        return result;
    }

    private Long argumentAt(Object[] arguments, int index) {
        if (index < 0 || index >= arguments.length || !(arguments[index] instanceof Long value)) {
            return null;
        }
        return value;
    }

    private Map<String, Object> namedArguments(Method method, Object[] arguments) {
        String[] parameterNames = method.getParameters() == null
                ? new String[0]
                : java.util.Arrays.stream(method.getParameters()).map(parameter -> parameter.getName()).toArray(String[]::new);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index++) {
            String name = index < parameterNames.length ? parameterNames[index] : "argument" + index;
            values.put(name, arguments[index]);
        }
        return values;
    }

    private String deriveEntityType(String serviceClassName) {
        String withoutSuffix = serviceClassName.endsWith("Service")
                ? serviceClassName.substring(0, serviceClassName.length() - "Service".length())
                : serviceClassName;
        return toSnakeCase(withoutSuffix);
    }

    private String toUpperSnakeCase(String value) {
        return toSnakeCase(value).toUpperCase(Locale.ROOT);
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
