package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyCreateRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyUpdateRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CancellationPolicyRule;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CancellationPolicyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancellationPolicyServiceTest {

    @Mock
    private CancellationPolicyRepository cancellationPolicyRepository;

    private ObjectMapper objectMapper;
    private CancellationPolicyService cancellationPolicyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cancellationPolicyService = new CancellationPolicyService(
                cancellationPolicyRepository,
                objectMapper
        );
    }

    @Test
    void createCancellationPolicyNormalizesAggregateAndSortsRules() {
        CancellationPolicyCreateRequest request = new CancellationPolicyCreateRequest(
                " flexible ",
                " Flexible cancellation ",
                "  Refund by cancellation time  ",
                new BigDecimal("100.00"),
                false,
                null,
                List.of(rule(0, "0.00"), rule(72, "100.00"), rule(30, "50.00"))
        );
        when(cancellationPolicyRepository.existsByCodeIgnoreCase("FLEXIBLE")).thenReturn(false);
        when(cancellationPolicyRepository.save(any(CancellationPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = cancellationPolicyService.createCancellationPolicy(request);

        assertEquals("FLEXIBLE", response.code());
        assertEquals("Flexible cancellation", response.name());
        assertEquals("Refund by cancellation time", response.description());
        assertTrue(response.isActive());
        assertEquals(List.of(72, 30, 0), response.rules().stream()
                .map(item -> item.minHoursBefore())
                .toList());

        ArgumentCaptor<CancellationPolicy> policyCaptor = ArgumentCaptor.forClass(CancellationPolicy.class);
        verify(cancellationPolicyRepository).save(policyCaptor.capture());
        assertTrue(policyCaptor.getValue().getRules().stream()
                .allMatch(item -> item.getPolicy() == policyCaptor.getValue()));
    }

    @Test
    void getActiveCancellationPoliciesReturnsRepositoryResultsInOrder() {
        CancellationPolicy flexible = policy("FLEXIBLE", true, true);
        addRule(flexible, 0, "0.00");
        CancellationPolicy moderate = policy("MODERATE", false, true);
        addRule(moderate, 0, "0.00");
        when(cancellationPolicyRepository.findAllByIsActiveTrueOrderByIsDefaultDescCodeAsc())
                .thenReturn(List.of(flexible, moderate));

        var responses = cancellationPolicyService.getActiveCancellationPolicies();

        assertEquals(List.of("FLEXIBLE", "MODERATE"), responses.stream()
                .map(item -> item.code())
                .toList());
        assertTrue(responses.stream().allMatch(item -> item.isActive()));
    }

    @Test
    void createCancellationPolicyRejectsDuplicateCode() {
        CancellationPolicyCreateRequest request = createRequest(List.of(rule(0, "0.00")));
        when(cancellationPolicyRepository.existsByCodeIgnoreCase("FLEXIBLE")).thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> cancellationPolicyService.createCancellationPolicy(request)
        );
        verify(cancellationPolicyRepository, never()).save(any());
    }

    @Test
    void createCancellationPolicyRequiresZeroHourFallbackRule() {
        CancellationPolicyCreateRequest request = createRequest(List.of(
                rule(72, "100.00"),
                rule(30, "50.00")
        ));

        assertThrows(
                BusinessValidationException.class,
                () -> cancellationPolicyService.createCancellationPolicy(request)
        );
        verify(cancellationPolicyRepository, never()).save(any());
    }

    @Test
    void createCancellationPolicyRejectsDuplicateRuleHours() {
        CancellationPolicyCreateRequest request = createRequest(List.of(
                rule(0, "0.00"),
                rule(0, "50.00")
        ));

        assertThrows(
                BusinessValidationException.class,
                () -> cancellationPolicyService.createCancellationPolicy(request)
        );
        verify(cancellationPolicyRepository, never()).save(any());
    }

    @Test
    void updateCancellationPolicyReplacesRulesAndMovesDefaultFlag() {
        CancellationPolicy currentDefault = policy("MODERATE", true, true);
        currentDefault.setId(1L);
        CancellationPolicy target = policy("FLEXIBLE", false, true);
        target.setId(2L);
        addRule(target, 0, "0.00");

        when(cancellationPolicyRepository.findByCodeIgnoreCase("FLEXIBLE"))
                .thenReturn(Optional.of(target));
        when(cancellationPolicyRepository.findDefaultPoliciesForUpdate())
                .thenReturn(List.of(currentDefault));
        when(cancellationPolicyRepository.save(target)).thenReturn(target);

        var response = cancellationPolicyService.updateCancellationPolicy(
                "flexible",
                new CancellationPolicyUpdateRequest(
                        "Flexible updated",
                        null,
                        new BigDecimal("75.00"),
                        true,
                        true,
                        List.of(rule(48, "100.00"), rule(0, "25.00"))
                )
        );

        assertFalse(currentDefault.getIsDefault());
        assertTrue(target.getIsDefault());
        assertEquals(List.of(48, 0), response.rules().stream()
                .map(item -> item.minHoursBefore())
                .toList());
        assertTrue(target.getRules().stream().allMatch(item -> item.getPolicy() == target));
    }

    @Test
    void deleteCancellationPolicyOnlyDeactivatesPolicy() {
        CancellationPolicy policy = policy("FLEXIBLE", true, true);
        when(cancellationPolicyRepository.findByCodeIgnoreCase("FLEXIBLE"))
                .thenReturn(Optional.of(policy));

        cancellationPolicyService.deleteCancellationPolicy("flexible");

        assertFalse(policy.getIsActive());
        assertTrue(policy.getIsDefault());
        verify(cancellationPolicyRepository).save(policy);
        verify(cancellationPolicyRepository, never()).delete(any());
    }

    @Test
    void applyPolicySnapshotUsesActivePolicyAndDescendingRules() throws Exception {
        CancellationPolicy policy = policy("FLEXIBLE", true, true);
        policy.setName("Flexible");
        policy.setNoShowChargePercent(new BigDecimal("100.00"));
        addRule(policy, 0, "0.00");
        addRule(policy, 72, "100.00");
        addRule(policy, 30, "50.00");
        Booking booking = new Booking();
        when(cancellationPolicyRepository.findByCodeIgnoreCaseAndIsActiveTrue("FLEXIBLE"))
                .thenReturn(Optional.of(policy));

        cancellationPolicyService.applyPolicySnapshot(booking, "flexible");

        assertSame(policy, booking.getCancellationPolicy());
        JsonNode snapshot = objectMapper.readTree(booking.getCancellationPolicySnapshot());
        assertEquals("FLEXIBLE", snapshot.get("code").asText());
        assertEquals("Flexible", snapshot.get("name").asText());
        assertEquals(0, snapshot.get("no_show_charge_percent").decimalValue()
                .compareTo(new BigDecimal("100.00")));
        assertEquals(List.of(72, 30, 0), snapshot.get("rules").findValues("min_hours_before")
                .stream()
                .map(JsonNode::asInt)
                .toList());
        assertFalse(snapshot.has("description"));
        assertFalse(snapshot.has("is_active"));
    }

    @Test
    void applyPolicySnapshotRejectsInactiveOrUnknownPolicy() {
        when(cancellationPolicyRepository.findByCodeIgnoreCaseAndIsActiveTrue("NON_REFUND"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> cancellationPolicyService.applyPolicySnapshot(new Booking(), "NON_REFUND")
        );
    }

    private CancellationPolicyCreateRequest createRequest(List<CancellationPolicyRuleRequest> rules) {
        return new CancellationPolicyCreateRequest(
                "FLEXIBLE",
                "Flexible",
                null,
                new BigDecimal("100.00"),
                false,
                true,
                rules
        );
    }

    private CancellationPolicyRuleRequest rule(int minHoursBefore, String refundPercent) {
        return new CancellationPolicyRuleRequest(
                minHoursBefore,
                new BigDecimal(refundPercent)
        );
    }

    private CancellationPolicy policy(String code, boolean isDefault, boolean isActive) {
        return CancellationPolicy.builder()
                .code(code)
                .name(code)
                .noShowChargePercent(new BigDecimal("100.00"))
                .isDefault(isDefault)
                .isActive(isActive)
                .build();
    }

    private void addRule(CancellationPolicy policy, int minHoursBefore, String refundPercent) {
        policy.getRules().add(CancellationPolicyRule.builder()
                .policy(policy)
                .minHoursBefore(minHoursBefore)
                .refundPercent(new BigDecimal(refundPercent))
                .build());
    }
}
