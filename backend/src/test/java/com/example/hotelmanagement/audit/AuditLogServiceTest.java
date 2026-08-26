package com.example.hotelmanagement.audit;

import com.example.hotelmanagement.entity.AuditLog;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void recordRedactsCredentialsBeforeSavingAuditLog() throws Exception {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService service = new AuditLogService(auditLogRepository, new ObjectMapper());

        service.record(
                "USER_UPDATED",
                "user",
                10L,
                20L,
                Map.of("password", "plaintext-password", "nested", Map.of("refreshToken", "secret-token")),
                Map.of("status", "ACTIVE")
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog auditLog = captor.getValue();
        JsonNode beforeData = new ObjectMapper().readTree(auditLog.getBeforeData());
        assertThat(auditLog.getActorUserId()).isEqualTo(20L);
        assertThat(beforeData.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(beforeData.at("/nested/refreshToken").asText()).isEqualTo("[REDACTED]");
        assertThat(auditLog.getAfterData()).contains("ACTIVE");
    }
}
