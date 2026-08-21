package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleRequest;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyUpdateRequest;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CancellationPolicyRule;
import com.example.hotelmanagement.repositories.CancellationPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CancellationPolicyServiceIntegrationTest {

    @Autowired
    private CancellationPolicyService cancellationPolicyService;

    @Autowired
    private CancellationPolicyRepository cancellationPolicyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(authorities = "policy:manage")
    void updateRulesReconcilesExistingRowsWithoutUniqueConstraintConflict() {
        CancellationPolicy policy = CancellationPolicy.builder()
                .code("FLEXIBLE")
                .name("Flexible")
                .noShowChargePercent(new BigDecimal("100.00"))
                .isDefault(false)
                .isActive(true)
                .build();
        addRule(policy, 72, "100.00");
        addRule(policy, 30, "50.00");
        addRule(policy, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(policy);

        cancellationPolicyService.updateCancellationPolicy(
                "FLEXIBLE",
                new CancellationPolicyUpdateRequest(
                        "Flexible updated",
                        null,
                        new BigDecimal("100.00"),
                        null,
                        true,
                        List.of(
                                rule(96, "100.00"),
                                rule(30, "75.00"),
                                rule(0, "10.00")
                        )
                )
        );
        cancellationPolicyRepository.flush();
        entityManager.clear();

        CancellationPolicy updatedPolicy = cancellationPolicyRepository
                .findByCodeIgnoreCase("FLEXIBLE")
                .orElseThrow();
        Map<Integer, BigDecimal> refundByHours = updatedPolicy.getRules().stream()
                .collect(Collectors.toMap(
                        CancellationPolicyRule::getMinHoursBefore,
                        CancellationPolicyRule::getRefundPercent
                ));

        assertEquals(3, refundByHours.size());
        assertEquals(0, refundByHours.get(96).compareTo(new BigDecimal("100.00")));
        assertEquals(0, refundByHours.get(30).compareTo(new BigDecimal("75.00")));
        assertEquals(0, refundByHours.get(0).compareTo(new BigDecimal("10.00")));
    }

    @Test
    @WithMockUser(authorities = "policy:manage")
    void settingNewDefaultClearsPreviousDefaultInSameTransaction() {
        CancellationPolicy flexible = policy("FLEXIBLE", true);
        addRule(flexible, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(flexible);

        CancellationPolicy moderate = policy("MODERATE", false);
        addRule(moderate, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(moderate);

        cancellationPolicyService.updateCancellationPolicy(
                "MODERATE",
                new CancellationPolicyUpdateRequest(
                        "Moderate",
                        null,
                        new BigDecimal("100.00"),
                        true,
                        true,
                        List.of(rule(0, "0.00"))
                )
        );
        cancellationPolicyRepository.flush();
        entityManager.clear();

        List<String> defaultCodes = cancellationPolicyRepository
                .findAllByOrderByIsDefaultDescCodeAsc()
                .stream()
                .filter(CancellationPolicy::getIsDefault)
                .map(CancellationPolicy::getCode)
                .toList();

        assertEquals(List.of("MODERATE"), defaultCodes);
    }

    private CancellationPolicyRuleRequest rule(int minHoursBefore, String refundPercent) {
        return new CancellationPolicyRuleRequest(
                minHoursBefore,
                new BigDecimal(refundPercent)
        );
    }

    private CancellationPolicy policy(String code, boolean isDefault) {
        return CancellationPolicy.builder()
                .code(code)
                .name(code)
                .noShowChargePercent(new BigDecimal("100.00"))
                .isDefault(isDefault)
                .isActive(true)
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
