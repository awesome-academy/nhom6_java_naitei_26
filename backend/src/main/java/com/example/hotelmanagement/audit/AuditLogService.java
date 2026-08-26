package com.example.hotelmanagement.audit;

import com.example.hotelmanagement.entity.AuditLog;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.example.hotelmanagement.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;

/** Persists safe, structured records for business mutations. */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_HEADER_LENGTH = 1_000;
    private static final int MAX_VALUE_LENGTH = 4_000;
    private static final Set<String> SENSITIVE_FIELD_TOKENS = Set.of(
            "password", "token", "secret", "authorization", "credential", "id_document"
    );

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            String action,
            String entityType,
            Long entityId,
            Long actorUserId,
            Object before,
            Object after
    ) {
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(actorUserId == null ? currentActorUserId() : actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeData(writeSafeJson(before))
                .afterData(writeSafeJson(after))
                .ipAddress(currentIpAddress())
                .userAgent(currentUserAgent())
                .build());
    }

    String writeSafeJson(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            redactSensitiveData(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            log.warn("Unable to serialize audit data valueType={}", valueType(value), exception);
            return "{\"serialization_error\":true}";
        }
    }

    private void redactSensitiveData(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (isSensitiveField(entry.getKey())) {
                    objectNode.put(entry.getKey(), "[REDACTED]");
                } else if (entry.getValue().isTextual()
                        && entry.getValue().textValue().length() > MAX_VALUE_LENGTH) {
                    objectNode.put(entry.getKey(), truncate(entry.getValue().textValue(), MAX_VALUE_LENGTH));
                } else {
                    redactSensitiveData(entry.getValue());
                }
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redactSensitiveData);
            return;
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_TOKENS.stream().anyMatch(normalized::contains);
    }

    private Long currentActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getRemoteAddr(), 45);
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), MAX_HEADER_LENGTH);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String valueType(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
