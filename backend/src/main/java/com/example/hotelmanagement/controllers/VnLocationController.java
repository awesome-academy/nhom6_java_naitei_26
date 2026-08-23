package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/vn", produces = "application/json")
@Tag(name = "Vietnam Locations", description = "Vietnam administrative divisions")
public class VnLocationController {

    @GetMapping("/provinces")
    @Operation(
        summary = "Get all provinces/cities",
        description = "Returns list of all provinces and cities in Vietnam for address selection",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "List of provinces",
                content = @Content(schema = @Schema(example = "[{\"id\":\"01\",\"name\":\"Hà Nội\"},{\"id\":\"79\",\"name\":\"Hồ Chí Minh\"}]"))
            )
        }
    )
    public ResponseEntity<List<Map<String, String>>> getProvinces() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/data/vn-provinces.json");
        if (inputStream == null) {
            return ResponseEntity.ok(List.of());
        }

        try (inputStream) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> provinces = mapper.readValue(inputStream, List.class);

            List<Map<String, String>> result = provinces.stream()
                .filter(p -> p instanceof Map)
                .map(p -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> province = (Map<String, Object>) p;
                    @SuppressWarnings("unchecked")
                    Map<String, String> nameObj = (Map<String, String>) province.get("name");
                    return Map.of(
                        "id", String.valueOf(province.get("id")),
                        "name", nameObj != null ? nameObj.getOrDefault("local", "") : ""
                    );
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        }
    }
}
