package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.roomtype.RoomTypeResponse;
import com.example.hotelmanagement.service.RoomTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomTypeAuthorizationTest {

    private static final String ROOM_TYPE_CODE = "DLX";
    private static final String CREATE_REQUEST = """
        {
          "code": "DLX",
          "name": "Deluxe",
          "maxOccupancy": 2,
          "maxAdults": 2,
          "basePrice": 1500000.00,
          "beds": [{"bedType": "QUEEN", "quantity": 1}],
          "amenityCodes": ["WIFI", "AC"]
        }
        """;
    private static final String UPDATE_REQUEST = """
        {
          "name": "Deluxe Updated",
          "maxOccupancy": 3,
          "maxAdults": 2,
          "maxChildren": 1,
          "basePrice": 1700000.00,
          "currency": "VND"
        }
        """;
    private static final String BEDS_REQUEST = """
        {"beds": [{"bedType": "KING", "quantity": 1}]}
        """;
    private static final String AMENITIES_REQUEST = """
        {"amenityCodes": ["WIFI", "TV"]}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomTypeService roomTypeService;

    @Test
    void roomTypeEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/room-types"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(roomTypeService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadPermissionAllowsReadingRoomTypes() throws Exception {
        when(roomTypeService.getRoomTypes()).thenReturn(List.of());

        mockMvc.perform(get("/api/room-types"))
            .andExpect(status().isOk());

        verify(roomTypeService).getRoomTypes();
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void missingRoomReadPermissionReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/room-types"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(roomTypeService);
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void roomCreatePermissionAllowsCreatingRoomType() throws Exception {
        when(roomTypeService.createRoomType(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/room-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST))
            .andExpect(status().isCreated());

        verify(roomTypeService).createRoomType(any());
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void missingRoomCreatePermissionReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/room-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_REQUEST))
            .andExpect(status().isForbidden());

        verifyNoInteractions(roomTypeService);
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void roomUpdatePermissionAllowsUpdatingRoomTypeBedsAndAmenities() throws Exception {
        RoomTypeResponse response = createResponse();
        when(roomTypeService.updateRoomType(eq(ROOM_TYPE_CODE), any())).thenReturn(response);
        when(roomTypeService.replaceRoomTypeBeds(eq(ROOM_TYPE_CODE), any())).thenReturn(response);
        when(roomTypeService.replaceRoomTypeAmenities(eq(ROOM_TYPE_CODE), any())).thenReturn(response);

        mockMvc.perform(put("/api/room-types/{code}", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_REQUEST))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/room-types/{code}/beds", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEDS_REQUEST))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/room-types/{code}/amenities", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AMENITIES_REQUEST))
            .andExpect(status().isOk());

        verify(roomTypeService).updateRoomType(eq(ROOM_TYPE_CODE), any());
        verify(roomTypeService).replaceRoomTypeBeds(eq(ROOM_TYPE_CODE), any());
        verify(roomTypeService).replaceRoomTypeAmenities(eq(ROOM_TYPE_CODE), any());
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void missingRoomUpdatePermissionBlocksAllRoomTypeUpdates() throws Exception {
        mockMvc.perform(put("/api/room-types/{code}", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_REQUEST))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/room-types/{code}/beds", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEDS_REQUEST))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/room-types/{code}/amenities", ROOM_TYPE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AMENITIES_REQUEST))
            .andExpect(status().isForbidden());

        verifyNoInteractions(roomTypeService);
    }

    @Test
    @WithMockUser(authorities = "room:delete")
    void roomDeletePermissionAllowsSoftDeletingRoomType() throws Exception {
        mockMvc.perform(delete("/api/room-types/{code}", ROOM_TYPE_CODE))
            .andExpect(status().isNoContent());

        verify(roomTypeService).deleteRoomType(ROOM_TYPE_CODE);
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void missingRoomDeletePermissionReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/room-types/{code}", ROOM_TYPE_CODE))
            .andExpect(status().isForbidden());

        verifyNoInteractions(roomTypeService);
    }

    private RoomTypeResponse createResponse() {
        return new RoomTypeResponse(
            ROOM_TYPE_CODE,
            "Deluxe",
            "deluxe",
            null,
            1,
            2,
            2,
            0,
            new BigDecimal("1500000.00"),
            "VND",
            null,
            null,
            true,
            10,
            List.of(),
            List.of(),
            null,
            null
        );
    }
}
