package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.amenity.AmenityDetailResponse;
import com.example.hotelmanagement.dto.amenity.AmenityFilterOptionResponse;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import com.example.hotelmanagement.services.AmenityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AmenityAuthorizationTest {

    private static final String AMENITY_CODE = "WIFI";
    private static final String CREATE_REQUEST = """
        {
          "code": "wifi",
          "name": "Wi-Fi",
          "icon": "wifi",
          "category": "TECH",
          "isFilterable": true,
          "sortOrder": 10
        }
        """;
    private static final String UPDATE_REQUEST = """
        {
          "name": "Wireless internet",
          "icon": "wifi",
          "category": "TECH",
          "isFilterable": false,
          "sortOrder": 20
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AmenityService amenityService;

    @Test
    void amenityEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/amenities/filter-options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(amenityService);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void roomReadPermissionAllowsListDetailAndFilterOptions() throws Exception {
        when(amenityService.getAmenities()).thenReturn(List.of(createResponse()));
        when(amenityService.getAmenity(AMENITY_CODE)).thenReturn(createResponse());
        when(amenityService.getFilterOptions()).thenReturn(List.of(
                new AmenityFilterOptionResponse(
                        AMENITY_CODE,
                        "Wi-Fi",
                        "wifi",
                        AmenityCategory.TECH,
                        10
                )
        ));

        mockMvc.perform(get("/api/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(AMENITY_CODE));
        mockMvc.perform(get("/api/amenities/{code}", AMENITY_CODE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/amenities/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isFilterable").doesNotExist());

        verify(amenityService).getAmenities();
        verify(amenityService).getAmenity(AMENITY_CODE);
        verify(amenityService).getFilterOptions();
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void roomCreatePermissionAllowsCreatingAmenity() throws Exception {
        when(amenityService.createAmenity(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/amenities/WIFI"));

        verify(amenityService).createAmenity(any());
    }

    @Test
    @WithMockUser(authorities = "room:update")
    void roomUpdatePermissionAllowsUpdatingAmenity() throws Exception {
        when(amenityService.updateAmenity(eq(AMENITY_CODE), any())).thenReturn(createResponse());

        mockMvc.perform(put("/api/amenities/{code}", AMENITY_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_REQUEST))
                .andExpect(status().isOk());

        verify(amenityService).updateAmenity(eq(AMENITY_CODE), any());
    }

    @Test
    @WithMockUser(authorities = "room:delete")
    void roomDeletePermissionAllowsDeletingAmenity() throws Exception {
        mockMvc.perform(delete("/api/amenities/{code}", AMENITY_CODE))
                .andExpect(status().isNoContent());

        verify(amenityService).deleteAmenity(AMENITY_CODE);
    }

    @Test
    @WithMockUser(authorities = "room:read")
    void missingMutationPermissionsReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/amenities/{code}", AMENITY_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_REQUEST))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/amenities/{code}", AMENITY_CODE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(amenityService);
    }

    @Test
    @WithMockUser(authorities = "room:create")
    void invalidAmenityRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "code": "invalid code",
                              "name": " ",
                              "category": "TECH",
                              "sortOrder": -1
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.sortOrder").exists());

        verifyNoInteractions(amenityService);
    }

    private AmenityDetailResponse createResponse() {
        return new AmenityDetailResponse(
                AMENITY_CODE,
                "Wi-Fi",
                "wifi",
                AmenityCategory.TECH,
                true,
                10,
                null,
                null
        );
    }
}
