package com.example.hotelmanagement.security;

public final class PermissionExpressions {

    public static final String ROOM_READ = "hasAuthority('room:read')";
    public static final String ROOM_CREATE = "hasAuthority('room:create')";
    public static final String ROOM_UPDATE = "hasAuthority('room:update')";
    public static final String ROOM_DELETE = "hasAuthority('room:delete')";
    public static final String SHIFT_MANAGE = "hasAuthority('shift:manage')";
    public static final String RBAC_READ = "hasAuthority('rbac:read')";
    public static final String RBAC_MANAGE = "hasAuthority('rbac:manage')";
    public static final String STAFF_MANAGE = "hasAuthority('staff:manage')";
    public static final String PRICING_MANAGE = "hasAuthority('pricing:manage')";

    private PermissionExpressions() {
    }
}
