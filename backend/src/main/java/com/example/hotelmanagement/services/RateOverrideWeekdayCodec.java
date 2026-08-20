package com.example.hotelmanagement.services;

import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Component
public class RateOverrideWeekdayCodec {

    private static final Logger log = LoggerFactory.getLogger(RateOverrideWeekdayCodec.class);
    private static final TypeReference<List<Integer>> WEEKDAY_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RateOverrideWeekdayCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeWeekdays(Set<Integer> weekdays, Long rateOverrideId) {
        if (weekdays == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new TreeSet<>(weekdays));
        } catch (JsonProcessingException exception) {
            log.error("Cannot serialize weekdays for rateOverrideId={}", rateOverrideId, exception);
            throw new PricingConfigurationException(
                    "Cannot serialize weekdays for rate override " + rateOverrideId,
                    exception
            );
        }
    }

    public Set<Integer> decodeWeekdays(String weekdays, Long rateOverrideId) {
        if (weekdays == null) {
            return null;
        }

        List<Integer> parsedWeekdays;
        try {
            parsedWeekdays = objectMapper.readValue(weekdays, WEEKDAY_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            log.error("Cannot parse weekdays for rateOverrideId={}", rateOverrideId, exception);
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " has malformed weekdays",
                    exception
            );
        }
        if (parsedWeekdays == null) {
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " weekdays must be a JSON array"
            );
        }

        Set<Integer> normalizedWeekdays = new LinkedHashSet<>();
        for (Integer weekday : parsedWeekdays) {
            if (weekday == null || weekday < 1 || weekday > 7) {
                throw new PricingConfigurationException(
                        "Rate override " + rateOverrideId
                                + " has a weekday outside the supported range 1-7"
                );
            }
            normalizedWeekdays.add(weekday);
        }
        return Collections.unmodifiableSet(normalizedWeekdays);
    }
}
