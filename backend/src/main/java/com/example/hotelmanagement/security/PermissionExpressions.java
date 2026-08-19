package com.example.hotelmanagement.security;

public final class PermissionExpressions {

    public static final String ROOM_READ = "hasAuthority('room:read')";
    public static final String ROOM_CREATE = "hasAuthority('room:create')";
    public static final String ROOM_UPDATE = "hasAuthority('room:update')";
    public static final String ROOM_DELETE = "hasAuthority('room:delete')";
    public static final String SHIFT_MANAGE = "hasAuthority('shift:manage')";

    private PermissionExpressions() {
    }
}
