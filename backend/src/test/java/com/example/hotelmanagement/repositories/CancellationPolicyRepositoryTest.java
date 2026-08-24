package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CancellationPolicyRule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class CancellationPolicyRepositoryTest {

    @Autowired
    private CancellationPolicyRepository cancellationPolicyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void listOrdersDefaultFirstAndFetchesRules() {
        CancellationPolicy moderate = policy("MODERATE", false, true);
        addRule(moderate, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(moderate);

        CancellationPolicy flexible = policy("FLEXIBLE", true, true);
        addRule(flexible, 72, "100.00");
        addRule(flexible, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(flexible);
        entityManager.clear();

        var policies = cancellationPolicyRepository.findAllByOrderByIsDefaultDescCodeAsc();

        assertEquals(2, policies.size());
        assertEquals("FLEXIBLE", policies.get(0).getCode());
        assertEquals(2, policies.get(0).getRules().size());
    }

    @Test
    void activeLookupExcludesInactivePolicyAndIgnoresCodeCase() {
        cancellationPolicyRepository.saveAndFlush(policy("NON_REFUND", false, false));

        assertFalse(cancellationPolicyRepository
                .findByCodeIgnoreCaseAndIsActiveTrue("non_refund")
                .isPresent());
        assertTrue(cancellationPolicyRepository.findByCodeIgnoreCase("non_refund").isPresent());
    }

    @Test
    void activeListExcludesInactivePoliciesAndOrdersDefaultFirst() {
        CancellationPolicy inactive = policy("NON_REFUND", false, false);
        addRule(inactive, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(inactive);

        CancellationPolicy moderate = policy("MODERATE", false, true);
        addRule(moderate, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(moderate);

        CancellationPolicy flexible = policy("FLEXIBLE", true, true);
        addRule(flexible, 72, "100.00");
        addRule(flexible, 0, "0.00");
        cancellationPolicyRepository.saveAndFlush(flexible);
        entityManager.clear();

        var policies = cancellationPolicyRepository
                .findAllByIsActiveTrueOrderByIsDefaultDescCodeAsc();

        assertEquals(2, policies.size());
        assertEquals("FLEXIBLE", policies.get(0).getCode());
        assertEquals("MODERATE", policies.get(1).getCode());
        assertEquals(2, policies.get(0).getRules().size());
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
