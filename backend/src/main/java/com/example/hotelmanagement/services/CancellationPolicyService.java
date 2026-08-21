package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyCreateRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleResponse;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleSnapshot;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicySnapshot;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyUpdateRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CancellationPolicyRule;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CancellationPolicyRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional
public class CancellationPolicyService {

    private static final BigDecimal MIN_PERCENT = BigDecimal.ZERO;
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.00");
    private static final int MAX_CODE_LENGTH = 30;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final CancellationPolicyRepository cancellationPolicyRepository;
    private final ObjectMapper objectMapper;

    public CancellationPolicyService(
            CancellationPolicyRepository cancellationPolicyRepository,
            ObjectMapper objectMapper
    ) {
        this.cancellationPolicyRepository = cancellationPolicyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    public List<CancellationPolicyResponse> getCancellationPolicies() {
        return cancellationPolicyRepository.findAllByOrderByIsDefaultDescCodeAsc()
                .stream()
                .map(this::mapPolicyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    public CancellationPolicyResponse getCancellationPolicy(String code) {
        return mapPolicyResponse(getExistingPolicy(code));
    }

    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    public CancellationPolicyResponse createCancellationPolicy(
            @Valid CancellationPolicyCreateRequest request
    ) {
        String normalizedCode = normalizeCode(request.code());
        if (cancellationPolicyRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateResourceException("Cancellation policy", "code", normalizedCode);
        }

        validatePercent(request.noShowChargePercent(), "No-show charge percent");
        validateRules(request.rules());

        CancellationPolicy policy = CancellationPolicy.builder()
                .code(normalizedCode)
                .name(normalizeRequiredText(request.name(), "Name"))
                .description(normalizeOptionalText(request.description()))
                .noShowChargePercent(request.noShowChargePercent())
                .isDefault(getValueOrDefault(request.isDefault(), false))
                .isActive(getValueOrDefault(request.isActive(), true))
                .build();

        replaceRules(policy, request.rules());
        updateDefaultPolicy(policy, policy.getIsDefault());

        return mapPolicyResponse(cancellationPolicyRepository.save(policy));
    }

    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    public CancellationPolicyResponse updateCancellationPolicy(
            String code,
            @Valid CancellationPolicyUpdateRequest request
    ) {
        CancellationPolicy policy = getExistingPolicy(code);
        validatePercent(request.noShowChargePercent(), "No-show charge percent");
        validateRules(request.rules());

        policy.setName(normalizeRequiredText(request.name(), "Name"));
        policy.setDescription(normalizeOptionalText(request.description()));
        policy.setNoShowChargePercent(request.noShowChargePercent());
        if (request.isActive() != null) {
            policy.setIsActive(request.isActive());
        }
        if (request.isDefault() != null) {
            updateDefaultPolicy(policy, request.isDefault());
        }
        replaceRules(policy, request.rules());

        return mapPolicyResponse(cancellationPolicyRepository.save(policy));
    }

    @PreAuthorize(PermissionExpressions.POLICY_MANAGE)
    public void deleteCancellationPolicy(String code) {
        CancellationPolicy policy = getExistingPolicy(code);
        policy.setIsActive(false);
        cancellationPolicyRepository.save(policy);
    }

    @PreAuthorize(PermissionExpressions.POLICY_USE_FOR_BOOKING)
    public void applyPolicySnapshot(Booking booking, String code) {
        if (booking == null) {
            throw new BusinessValidationException("Booking is required for policy snapshot");
        }

        CancellationPolicy policy = getActivePolicy(code);
        CancellationPolicySnapshot snapshot = mapPolicySnapshot(policy);
        booking.setCancellationPolicy(policy);
        booking.setCancellationPolicySnapshot(objectMapper.valueToTree(snapshot).toString());
    }

    private CancellationPolicy getExistingPolicy(String code) {
        String normalizedCode = normalizeCode(code);
        return cancellationPolicyRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Cancellation policy", normalizedCode));
    }

    private CancellationPolicy getActivePolicy(String code) {
        String normalizedCode = normalizeCode(code);
        return cancellationPolicyRepository.findByCodeIgnoreCaseAndIsActiveTrue(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Active cancellation policy", normalizedCode));
    }

    private void replaceRules(
            CancellationPolicy policy,
            List<CancellationPolicyRuleRequest> ruleRequests
    ) {
        Map<Integer, CancellationPolicyRule> existingRules = policy.getRules().stream()
                .collect(Collectors.toMap(
                        CancellationPolicyRule::getMinHoursBefore,
                        Function.identity()
                ));
        Set<Integer> requestedMinHours = ruleRequests.stream()
                .map(CancellationPolicyRuleRequest::minHoursBefore)
                .collect(Collectors.toSet());
        policy.getRules().removeIf(rule -> !requestedMinHours.contains(rule.getMinHoursBefore()));

        for (CancellationPolicyRuleRequest ruleRequest : ruleRequests) {
            CancellationPolicyRule existingRule = existingRules.get(ruleRequest.minHoursBefore());
            if (existingRule != null) {
                existingRule.setRefundPercent(ruleRequest.refundPercent());
            } else {
                policy.getRules().add(CancellationPolicyRule.builder()
                        .policy(policy)
                        .minHoursBefore(ruleRequest.minHoursBefore())
                        .refundPercent(ruleRequest.refundPercent())
                        .build());
            }
        }
    }

    private void updateDefaultPolicy(CancellationPolicy policy, boolean isDefault) {
        if (isDefault) {
            for (CancellationPolicy defaultPolicy : cancellationPolicyRepository.findDefaultPoliciesForUpdate()) {
                if (policy.getId() == null || !policy.getId().equals(defaultPolicy.getId())) {
                    defaultPolicy.setIsDefault(false);
                }
            }
        }
        policy.setIsDefault(isDefault);
    }

    private void validateRules(List<CancellationPolicyRuleRequest> ruleRequests) {
        if (ruleRequests == null || ruleRequests.isEmpty()) {
            throw new BusinessValidationException("A cancellation policy must have at least one rule");
        }

        Set<Integer> minHours = new HashSet<>();
        boolean hasFallbackRule = false;
        for (CancellationPolicyRuleRequest ruleRequest : ruleRequests) {
            if (ruleRequest == null
                    || ruleRequest.minHoursBefore() == null
                    || ruleRequest.refundPercent() == null) {
                throw new BusinessValidationException("Rule hours and refund percent are required");
            }
            if (ruleRequest.minHoursBefore() < 0) {
                throw new BusinessValidationException("Minimum hours before cancellation cannot be negative");
            }
            validatePercent(ruleRequest.refundPercent(), "Refund percent");
            if (!minHours.add(ruleRequest.minHoursBefore())) {
                throw new BusinessValidationException("Rule minimum hours must be unique within a policy");
            }
            if (ruleRequest.minHoursBefore() == 0) {
                hasFallbackRule = true;
            }
        }

        if (!hasFallbackRule) {
            throw new BusinessValidationException("A cancellation policy must include a rule at 0 hours");
        }
    }

    private void validatePercent(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(MIN_PERCENT) < 0 || value.compareTo(MAX_PERCENT) > 0) {
            throw new BusinessValidationException(fieldName + " must be between 0 and 100");
        }
    }

    private CancellationPolicyResponse mapPolicyResponse(CancellationPolicy policy) {
        List<CancellationPolicyRuleResponse> rules = policy.getRules().stream()
                .sorted(Comparator.comparing(CancellationPolicyRule::getMinHoursBefore).reversed())
                .map(rule -> new CancellationPolicyRuleResponse(
                        rule.getMinHoursBefore(),
                        rule.getRefundPercent()
                ))
                .toList();

        return new CancellationPolicyResponse(
                policy.getCode(),
                policy.getName(),
                policy.getDescription(),
                policy.getNoShowChargePercent(),
                policy.getIsDefault(),
                policy.getIsActive(),
                rules,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

    private CancellationPolicySnapshot mapPolicySnapshot(CancellationPolicy policy) {
        List<CancellationPolicyRuleSnapshot> rules = policy.getRules().stream()
                .sorted(Comparator.comparing(CancellationPolicyRule::getMinHoursBefore).reversed())
                .map(rule -> new CancellationPolicyRuleSnapshot(
                        rule.getMinHoursBefore(),
                        rule.getRefundPercent()
                ))
                .toList();
        return new CancellationPolicySnapshot(
                policy.getCode(),
                policy.getName(),
                policy.getNoShowChargePercent(),
                rules
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Policy code cannot be blank");
        }
        String normalizedCode = code.strip().toUpperCase(Locale.ROOT);
        if (normalizedCode.length() > MAX_CODE_LENGTH || !CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new BusinessValidationException("Policy code contains unsupported characters");
        }
        return normalizedCode;
    }

    private String normalizeRequiredText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return text.strip();
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalizedText = text.strip();
        return normalizedText.isEmpty() ? null : normalizedText;
    }

    private boolean getValueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
