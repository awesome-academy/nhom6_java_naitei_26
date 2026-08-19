package com.example.hotelmanagement.security;

import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String publicId;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final OffsetDateTime lockedUntil;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Set<GrantedAuthority> authorities;

    private UserPrincipal(
        Long id,
        String publicId,
        String email,
        String passwordHash,
        UserStatus status,
        OffsetDateTime lockedUntil,
        Set<String> roles,
        Set<String> permissions
    ) {
        this.id = id;
        this.publicId = publicId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.lockedUntil = lockedUntil;
        this.roles = roles;
        this.permissions = permissions;
        this.authorities = buildAuthorities(roles, permissions);
    }

    public static UserPrincipal from(User user) {
        Set<String> roleCodes = new LinkedHashSet<>();
        Set<String> permissionCodes = new LinkedHashSet<>();
        user.getUserRoles().forEach(userRole -> {
            roleCodes.add(userRole.getRole().getCode());
            userRole.getRole().getRolePermissions().forEach(rolePermission ->
                permissionCodes.add(rolePermission.getPermission().getCode())
            );
        });
        return new UserPrincipal(
            user.getId(),
            user.getPublicId(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getStatus(),
            user.getLockedUntil(),
            Set.copyOf(roleCodes),
            Set.copyOf(permissionCodes)
        );
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED
            && status != UserStatus.DEACTIVATED
            && (lockedUntil == null || !lockedUntil.isAfter(OffsetDateTime.now()));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.DEACTIVATED;
    }

    private static Set<GrantedAuthority> buildAuthorities(Set<String> roles, Set<String> permissions) {
        Set<GrantedAuthority> values = new LinkedHashSet<>();
        roles.forEach(role -> values.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> values.add(new SimpleGrantedAuthority(permission)));
        return Set.copyOf(values);
    }
}
