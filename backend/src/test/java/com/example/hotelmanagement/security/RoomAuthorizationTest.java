package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.room.RoomResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.services.RoomImageService;
import com.example.hotelmanagement.services.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomAuthorizationTest {

    private static final String ROOM_NUMBER = "A101";
    private static final String CREATE_REQUEST = """
            {
              "roomNumber": "A101",
              "roomTypeCode": "DLX",
              "viewType": "SEA",
              "floor": 1,
              "priceOverride": 1200000.00
            }
            """;
    private static final String UPDATE_REQUEST = """
            {
              "roomTypeCode": "DLX",
              "viewType": "CITY",
              "floor": 2,
              "priceOverride": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomService roomService;

    @MockBean
    private RoomImageService roomImageService;

    @Test
    void roomEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(roomService, roomImageService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadPermissionAllowsListAndDetail() throws Exception {
        when(roomService.getRooms(any(), any(), any(), any())).thenReturn(List.of());
        when(roomService.getRoom(ROOM_NUMBER)).thenReturn(roomResponse());

        mockMvc.perform(get("/api/rooms").param("amenityCodes", "WIFI", "BALCONY"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/{roomNumber}", ROOM_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value(ROOM_NUMBER));

        verify(roomService).getRoom(ROOM_NUMBER);
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void missingReadPermissionReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomService, roomImageService);
    }

    @Test
    void roomCreatePermissionAllowsCreateAndPassesAuthenticatedUserId() throws Exception {
        UserPrincipal principal = principal(99L);
        when(roomService.createRoom(any(), eq(99L))).thenReturn(roomResponse());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("room:create"))
        );

        mockMvc.perform(post("/api/rooms")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value(ROOM_NUMBER));

        verify(roomService).createRoom(any(), eq(99L));
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void roomUpdatePermissionAllowsRoomHousekeepingAndImageOperations() throws Exception {
        UUID imageId = UUID.randomUUID();
        OffsetDateTime expiry = OffsetDateTime.of(2026, 8, 19, 10, 0, 0, 0, ZoneOffset.UTC);
        when(roomService.updateRoom(eq(ROOM_NUMBER), any())).thenReturn(roomResponse());
        when(roomService.updateHousekeepingStatus(eq(ROOM_NUMBER), any())).thenReturn(roomResponse());
        when(roomImageService.createUploadUrl(eq(ROOM_NUMBER), any()))
                .thenReturn(new RoomImageUploadUrlResponse(
                        imageId, "https://upload.example", Map.of("Content-Type", "image/jpeg"), expiry
                ));
        when(roomImageService.confirmUpload(eq(ROOM_NUMBER), any()))
                .thenReturn(new RoomImageResponse(
                        imageId, "https://download.example", expiry, "Room", true, 0
                ));
        when(roomImageService.reorderImages(eq(ROOM_NUMBER), any())).thenReturn(List.of());

        mockMvc.perform(put("/api/rooms/{roomNumber}", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_REQUEST))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rooms/{roomNumber}/housekeeping-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIRTY\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomNumber}/images/upload-url", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"room.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1024}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomNumber}/images/confirm", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + imageId + "\",\"altText\":\"Room\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/rooms/{roomNumber}/images/order", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[\"" + imageId + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void missingUpdatePermissionBlocksHousekeepingAndImages() throws Exception {
        mockMvc.perform(patch("/api/rooms/{roomNumber}/housekeeping-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIRTY\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/{roomNumber}/images/upload-url", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"room.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1024}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomService, roomImageService);
    }

    @Test
    @WithMockUser(authorities = "room:delete")
    void roomDeletePermissionAllowsSoftDelete() throws Exception {
        mockMvc.perform(delete("/api/rooms/{roomNumber}", ROOM_NUMBER))
                .andExpect(status().isNoContent());

        verify(roomService).deleteRoom(ROOM_NUMBER);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void invalidEnumQueryReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rooms").param("viewType", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    private RoomResponse roomResponse() {
        return new RoomResponse(
                ROOM_NUMBER,
                "DLX",
                "Deluxe",
                RoomView.SEA,
                1,
                RoomOperationalStatus.ACTIVE,
                HousekeepingStatus.CLEAN,
                new BigDecimal("1200000.00"),
                true,
                List.of(),
                List.of(),
                null,
                null
        );
    }

    private UserPrincipal principal(Long id) {
        com.example.hotelmanagement.entity.User user = com.example.hotelmanagement.entity.User.builder()
                .publicId(UUID.randomUUID().toString())
                .email("admin@example.com")
                .passwordHash("hash")
                .status(com.example.hotelmanagement.entity.enums.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
