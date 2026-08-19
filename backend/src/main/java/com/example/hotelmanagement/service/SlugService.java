package com.example.hotelmanagement.service;

import com.example.hotelmanagement.exception.BusinessValidationException;
import com.example.hotelmanagement.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SlugService {

    private static final int MAX_SLUG_LENGTH = 140;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final Pattern TRAILING_HYPHENS = Pattern.compile("-+$");

    private final RoomTypeRepository roomTypeRepository;

    public SlugService(RoomTypeRepository roomTypeRepository) {
        this.roomTypeRepository = roomTypeRepository;
    }

    public String generateUniqueSlug(String name) {
        return generateUniqueSlug(name, null);
    }

    public String generateUniqueSlugForUpdate(String name, Long roomTypeId) {
        return generateUniqueSlug(name, roomTypeId);
    }

    private String generateUniqueSlug(String name, Long excludedRoomTypeId) {
        String baseSlug = generateBaseSlug(name);
        String candidate = baseSlug;
        int suffixNumber = 2;

        while (doesSlugExist(candidate, excludedRoomTypeId)) {
            String suffix = "-" + suffixNumber;
            candidate = truncateSlug(baseSlug, MAX_SLUG_LENGTH - suffix.length()) + suffix;
            suffixNumber++;
        }

        return candidate;
    }

    private String generateBaseSlug(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessValidationException("Room type name cannot produce an empty slug");
        }

        String vietnameseNormalized = name.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(vietnameseNormalized, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String hyphenated = NON_ALPHANUMERIC.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll("-");
        String baseSlug = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");

        if (baseSlug.isBlank()) {
            throw new BusinessValidationException("Room type name cannot produce an empty slug");
        }

        return truncateSlug(baseSlug, MAX_SLUG_LENGTH);
    }

    private String truncateSlug(String slug, int maxLength) {
        if (slug.length() <= maxLength) {
            return slug;
        }

        String truncated = slug.substring(0, maxLength);
        return TRAILING_HYPHENS.matcher(truncated).replaceAll("");
    }

    private boolean doesSlugExist(String slug, Long excludedRoomTypeId) {
        if (excludedRoomTypeId == null) {
            return roomTypeRepository.existsBySlug(slug);
        }

        return roomTypeRepository.existsBySlugAndIdNot(slug, excludedRoomTypeId);
    }
}
