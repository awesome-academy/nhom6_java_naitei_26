package com.example.hotelmanagement.security;

import com.example.hotelmanagement.repositories.AvailableRoomProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityEndpointSecurityTest {

    private static final String ENDPOINT = "/api/rooms/availability";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomRepository roomRepository;

    @Test
    void availabilityRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("checkInDate", "2026-09-10")
                        .param("checkOutDate", "2026-09-12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(roomRepository);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void availabilityRejectsUserWithoutBookingCreatePermission() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("checkInDate", "2026-09-10")
                        .param("checkOutDate", "2026-09-12"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(roomRepository);
    }

    @Test
    @WithMockUser(authorities = "booking:create")
    void availabilityReturnsRoomIdsGroupedByRoomType() throws Exception {
        when(roomRepository.findAvailableRooms(
                eq(LocalDate.of(2026, 9, 10)),
                eq(LocalDate.of(2026, 9, 12)),
                any(),
                any()
        )).thenReturn(List.of(
                projection(1L, 101L),
                projection(1L, 102L),
                projection(2L, 201L)
        ));

        mockMvc.perform(get(ENDPOINT)
                        .param("checkInDate", "2026-09-10")
                        .param("checkOutDate", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1'][0]").value(101))
                .andExpect(jsonPath("$['1'][1]").value(102))
                .andExpect(jsonPath("$['2'][0]").value(201));
    }

    @Test
    @WithMockUser(authorities = "booking:create")
    void availabilityRejectsMissingOrMalformedDates() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("checkInDate", "2026-09-10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ENDPOINT)
                        .param("checkInDate", "not-a-date")
                        .param("checkOutDate", "2026-09-12"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roomRepository);
    }

    @Test
    @WithMockUser(authorities = "booking:create")
    void availabilityRejectsInvalidStayPeriod() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("checkInDate", "2026-09-10")
                        .param("checkOutDate", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Check-out date must be after check-in date"));

        verifyNoInteractions(roomRepository);
    }

    private AvailableRoomProjection projection(Long roomTypeId, Long roomId) {
        return new AvailableRoomProjection() {
            @Override
            public Long getRoomTypeId() {
                return roomTypeId;
            }

            @Override
            public Long getRoomId() {
                return roomId;
            }
        };
    }
}
