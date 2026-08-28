package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.room.RoomResponse;
import com.example.hotelmanagement.dto.room.RoomBookingStatus;
import com.example.hotelmanagement.dto.room.RoomOccupancyResponse;
import com.example.hotelmanagement.dto.room.RoomBookingMapResponse;
import com.example.hotelmanagement.dto.room.RoomOperationalStatusResponse;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomBlockType;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.services.RoomImageService;
import com.example.hotelmanagement.services.BookingService;
import com.example.hotelmanagement.services.BookingCalculatorService;
import com.example.hotelmanagement.services.RoomOccupancyService;
import com.example.hotelmanagement.services.RoomBookingMapService;
import com.example.hotelmanagement.services.RoomService;
import com.example.hotelmanagement.services.RoomStatusBlockService;
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
import java.time.LocalDate;
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

    @MockBean
    private RoomStatusBlockService roomStatusBlockService;

    @MockBean
    private RoomOccupancyService roomOccupancyService;

    @MockBean
    private RoomBookingMapService roomBookingMapService;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private BookingCalculatorService bookingCalculatorService;

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
    void roomUpdatePermissionAllowsRoomAndImageOperations() throws Exception {
        UUID imageId = UUID.randomUUID();
        OffsetDateTime expiry = OffsetDateTime.of(2026, 8, 19, 10, 0, 0, 0, ZoneOffset.UTC);
        when(roomService.updateRoom(eq(ROOM_NUMBER), any())).thenReturn(roomResponse());
        when(roomImageService.createUploadUrl(eq(ROOM_NUMBER), any()))
                .thenReturn(new RoomImageUploadUrlResponse(
                        imageId, "https://upload.example", Map.of("Content-Type", "image/jpeg"), expiry
                ));
        when(roomImageService.confirmUpload(eq(ROOM_NUMBER), any()))
                .thenReturn(new RoomImageResponse(
                        imageId, "https://download.example", expiry, "Room", true, 0
                ));
        when(roomImageService.reorderImages(eq(ROOM_NUMBER), any())).thenReturn(List.of());
        when(roomStatusBlockService.updateOperationalStatus(eq(ROOM_NUMBER), any()))
                .thenReturn(new RoomOperationalStatusResponse(
                        ROOM_NUMBER,
                        RoomOperationalStatus.MAINTENANCE
                ));

        mockMvc.perform(put("/api/rooms/{roomNumber}", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_REQUEST))
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
        mockMvc.perform(patch("/api/rooms/{roomNumber}/operational-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("MAINTENANCE"));
    }

    @Test
    @WithMockUser(authorities = "room:housekeeping:update")
    void housekeepingPermissionAllowsHousekeepingUpdate() throws Exception {
        when(roomService.updateHousekeepingStatus(eq(ROOM_NUMBER), any())).thenReturn(roomResponse());

        mockMvc.perform(patch("/api/rooms/{roomNumber}/housekeeping-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIRTY\"}"))
                .andExpect(status().isOk());

        verify(roomService).updateHousekeepingStatus(eq(ROOM_NUMBER), any());
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void roomUpdatePermissionDoesNotAllowHousekeepingUpdate() throws Exception {
        mockMvc.perform(patch("/api/rooms/{roomNumber}/housekeeping-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIRTY\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomService);
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
        mockMvc.perform(patch("/api/rooms/{roomNumber}/operational-status", ROOM_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomService, roomImageService, roomStatusBlockService);
    }

    @Test
    void roomStatusBlockEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/room-status-blocks")
                        .param("startDate", "2026-09-10")
                        .param("endDate", "2026-09-12"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomStatusBlockService);
    }

    @Test
    void roomOccupancyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms/occupancy"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomOccupancyService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadPermissionDoesNotAllowRoomOccupancy() throws Exception {
        mockMvc.perform(get("/api/rooms/occupancy"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomOccupancyService);
    }

    @Test
    @WithMockUser(authorities = "room:occupancy:read")
    void roomOccupancyPermissionReturnsOccupancy() throws Exception {
        when(roomOccupancyService.getOccupancy(LocalDate.of(2026, 8, 25))).thenReturn(List.of(
                new RoomOccupancyResponse(ROOM_NUMBER, RoomBookingStatus.OCCUPIED)
        ));

        mockMvc.perform(get("/api/rooms/occupancy").param("date", "2026-08-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomNumber").value(ROOM_NUMBER))
                .andExpect(jsonPath("$[0].bookingStatus").value("OCCUPIED"));

        verify(roomOccupancyService).getOccupancy(LocalDate.of(2026, 8, 25));
    }

    @Test
    void staffRoomBookingMapRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/rooms/booking-map")
                        .param("checkInDate", "2026-09-01")
                        .param("checkOutDate", "2026-09-03"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomBookingMapService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadDoesNotGrantStaffRoomBookingMap() throws Exception {
        mockMvc.perform(get("/api/admin/rooms/booking-map")
                        .param("checkInDate", "2026-09-01")
                        .param("checkOutDate", "2026-09-03"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomBookingMapService);
    }

    @Test
    @WithMockUser(authorities = "room:booking_map:read")
    void roomBookingMapPermissionReturnsTimelineRooms() throws Exception {
        when(roomBookingMapService.getBookingMap(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)
        )).thenReturn(List.of(new RoomBookingMapResponse(
                1L,
                ROOM_NUMBER,
                "DLX",
                "Deluxe",
                RoomView.SEA,
                1,
                RoomOperationalStatus.ACTIVE,
                HousekeepingStatus.CLEAN,
                3,
                true,
                null,
                List.of()
        )));

        mockMvc.perform(get("/api/admin/rooms/booking-map")
                        .param("checkInDate", "2026-09-01")
                        .param("checkOutDate", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomNumber").value(ROOM_NUMBER))
                .andExpect(jsonPath("$[0].selectable").value(true));

        verify(roomBookingMapService).getBookingMap(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)
        );
    }

    @Test
    void staffBookingCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBookingRequest()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(authorities = "booking:create")
    void customerBookingPermissionDoesNotGrantStaffBookingCreation() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBookingRequest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    void staffBookingPermissionPassesAuthenticatedUserId() throws Exception {
        UserPrincipal principal = principal(99L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("booking:create_staff"))
        );

        mockMvc.perform(post("/api/admin/bookings")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBookingRequest()))
                .andExpect(status().isCreated());

        verify(bookingService).createStaffBooking(any(), eq(99L));
    }

    @Test
    @WithMockUser(authorities = "booking:create_staff")
    void staffBookingRequiresContactPhone() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBookingRequest().replace(
                                "\"contactPhone\": \"0900000000\",",
                                "\"contactPhone\": \"\","
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(authorities = "booking:create_staff")
    void staffBookingRequiresIdentityDocumentForEveryGuest() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBookingRequest().replace(
                                "\"idDocumentNumber\": \"012345678901\"",
                                "\"idDocumentNumber\": \"\""
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(authorities = "booking:create_staff")
    void staffBookingPermissionAllowsPriceCalculation() throws Exception {
        mockMvc.perform(post("/api/bookings/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomTypeCode":"DLX",
                                  "paymentOption":"ONLINE",
                                  "cancellationPolicyCode":"FLEXIBLE",
                                  "checkInDate":"2026-09-01",
                                  "checkOutDate":"2026-09-03",
                                  "adults":1,
                                  "children":0
                                }
                                """))
                .andExpect(status().isOk());

        verify(bookingCalculatorService).calculatePrice(any());
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadPermissionDoesNotAllowPriceCalculation() throws Exception {
        mockMvc.perform(post("/api/bookings/calculate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomTypeCode":"DLX",
                                  "paymentOption":"ONLINE",
                                  "cancellationPolicyCode":"FLEXIBLE",
                                  "checkInDate":"2026-09-01",
                                  "checkOutDate":"2026-09-03",
                                  "adults":1,
                                  "children":0
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingCalculatorService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomStatusBlockListRequiresBothDates() throws Exception {
        mockMvc.perform(get("/api/room-status-blocks")
                        .param("startDate", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(roomStatusBlockService);
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void roomStatusBlockReadRequiresRoomReadPermission() throws Exception {
        mockMvc.perform(get("/api/room-status-blocks")
                        .param("startDate", "2026-09-10")
                        .param("endDate", "2026-09-12"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomStatusBlockService);
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void roomUpdatePermissionDoesNotGrantMaintenanceBlockMutations() throws Exception {
        mockMvc.perform(post("/api/room-status-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomNumber":"A101",
                                  "blockType":"MAINTENANCE",
                                  "startDate":"2026-09-10",
                                  "endDate":"2026-09-12"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomStatusBlockService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomStatusBlockReadReturnsContract() throws Exception {
        UUID publicId = UUID.randomUUID();
        when(roomStatusBlockService.getBlocks(any(), any())).thenReturn(List.of(
                blockResponse(publicId)
        ));

        mockMvc.perform(get("/api/room-status-blocks")
                        .param("startDate", "2026-09-10")
                        .param("endDate", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value(publicId.toString()))
                .andExpect(jsonPath("$[0].roomNumber").value(ROOM_NUMBER));
    }

    @Test
    void maintenanceManagePermissionCreatesBlockAndUsesAuthenticatedUser() throws Exception {
        UUID publicId = UUID.randomUUID();
        UserPrincipal principal = principal(99L);
        when(roomStatusBlockService.createBlock(any(), eq(99L))).thenReturn(blockResponse(publicId));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("maintenance:manage"))
        );

        mockMvc.perform(post("/api/room-status-blocks")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomNumber":"a101",
                                  "blockType":"MAINTENANCE",
                                  "startDate":"2026-09-10",
                                  "endDate":"2026-09-12",
                                  "reason":"Air conditioner"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value(publicId.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", "/api/room-status-blocks/" + publicId));

        verify(roomStatusBlockService).createBlock(any(), eq(99L));
    }

    @Test
    @WithMockUser(authorities = "maintenance:manage")
    void maintenanceManagePermissionAllowsExtendAndDelete() throws Exception {
        UUID publicId = UUID.randomUUID();
        when(roomStatusBlockService.extendBlock(eq(publicId), any())).thenReturn(blockResponse(publicId));

        mockMvc.perform(patch("/api/room-status-blocks/{publicId}/extend", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEndDate\":\"2026-09-14\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/room-status-blocks/{publicId}", publicId))
                .andExpect(status().isNoContent());

        verify(roomStatusBlockService).deleteBlock(publicId);
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
                1L,
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

    private RoomStatusBlockResponse blockResponse(UUID publicId) {
        return new RoomStatusBlockResponse(
                publicId.toString(),
                ROOM_NUMBER,
                RoomOperationalStatus.ACTIVE,
                RoomBlockType.MAINTENANCE,
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 12),
                "Air conditioner",
                OffsetDateTime.of(2026, 8, 19, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 8, 19, 10, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    private String staffBookingRequest() {
        return """
                {
                  "contactName": "Nguyen Van A",
                  "contactEmail": "guest@example.com",
                  "contactPhone": "0900000000",
                  "rooms": [
                    {
                      "roomNumber": "A101",
                      "roomTypeCode": "DLX",
                      "paymentOption": "ONLINE",
                      "checkInDate": "2026-09-01",
                      "checkOutDate": "2026-09-03",
                      "guestCount": 2,
                      "guests": [
                        {
                          "fullName": "Nguyen Van A",
                          "nationality": "VN",
                          "idDocumentType": "NATIONAL_ID",
                          "idDocumentNumber": "012345678901"
                        },
                        {
                          "fullName": "Nguyen Van B",
                          "nationality": "VN",
                          "idDocumentType": "PASSPORT",
                          "idDocumentNumber": "P1234567"
                        }
                      ]
                    }
                  ]
                }
                """;
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
