package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.User;
import org.springframework.stereotype.Service;

@Service
public class AvatarUrlResolver {

    private final AvatarStorage avatarStorage;

    public AvatarUrlResolver(AvatarStorage avatarStorage) {
        this.avatarStorage = avatarStorage;
    }

    public String resolve(User user) {
        if (user.getAvatarStorageKey() == null || user.getAvatarStorageKey().isBlank()) {
            return user.getAvatarUrl();
        }
        return avatarStorage.createDownloadUrl(user.getAvatarStorageKey()).url();
    }
}
