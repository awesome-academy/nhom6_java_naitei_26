package com.example.hotelmanagement.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class VietnamProvinceService {

    private static Set<String> validProvinceNames = new HashSet<>();

    @PostConstruct
    public void loadProvinces() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = new ClassPathResource("data/vn-provinces.json").getInputStream();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> provinces = mapper.readValue(inputStream, List.class);

            for (Map<String, Object> province : provinces) {
                @SuppressWarnings("unchecked")
                Map<String, String> name = (Map<String, String>) province.get("name");
                if (name != null) {
                    String localName = name.get("local");
                    if (localName != null) {
                        validProvinceNames.add(localName.trim());
                    }
                }
            }

            log.info("Loaded {} valid Vietnam provinces", validProvinceNames.size());
        } catch (IOException e) {
            log.error("Failed to load Vietnam provinces", e);
        }
    }

    public static boolean isValidProvince(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return validProvinceNames.contains(name.trim());
    }

    public static Set<String> getValidProvinces() {
        return Set.copyOf(validProvinceNames);
    }
}
