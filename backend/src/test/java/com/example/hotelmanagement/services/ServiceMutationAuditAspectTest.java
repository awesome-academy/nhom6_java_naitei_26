package com.example.hotelmanagement.services;

import com.example.hotelmanagement.audit.AuditLogService;
import com.example.hotelmanagement.audit.AuditMutation;
import com.example.hotelmanagement.audit.ServiceMutationAuditAspect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ServiceMutationAuditAspectTest {

    @Test
    void updateMethodIsRecordedByAopWithInputsAndResult() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        MutationTarget proxy = createProxy(auditLogService);

        String result = proxy.updateName("new name");

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).record(
                eq("MUTATION_TARGET_UPDATE_NAME"),
                eq("mutation_target"),
                eq(null),
                eq(null),
                beforeCaptor.capture(),
                eq("updated:new name")
        );
        assertThat(result).isEqualTo("updated:new name");
        assertThat(beforeCaptor.getValue()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) beforeCaptor.getValue()).containsValue("new name")).isTrue();
    }

    @Test
    void annotatedMutationUsesSpecifiedActionAndActorArgument() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        MutationTarget proxy = createProxy(auditLogService);

        proxy.markNoShow("booking-public-id", 45L);

        verify(auditLogService).record(
                eq("BOOKING_NO_SHOW"),
                eq("booking"),
                eq(null),
                eq(45L),
                any(),
                eq("no-show")
        );
    }

    private MutationTarget createProxy(AuditLogService auditLogService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new MutationTarget());
        factory.addAspect(new ServiceMutationAuditAspect(auditLogService));
        return factory.getProxy();
    }

    public static class MutationTarget {

        public String updateName(String value) {
            return "updated:" + value;
        }

        @AuditMutation(action = "BOOKING_NO_SHOW", entityType = "booking", actorUserIdArgumentIndex = 1)
        public String markNoShow(String bookingPublicId, Long actorUserId) {
            return "no-show";
        }
    }
}
