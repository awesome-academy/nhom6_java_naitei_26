package com.example.hotelmanagement.security;

public final class PermissionExpressions {

    public static final String BOOKING_CREATE = "hasAuthority('booking:create')";
    public static final String BOOKING_READ_OWN = "hasAuthority('booking:read_own')";
    public static final String PAYMENT_CREATE = "hasAuthority('booking:create')";
    public static final String PAYMENT_MANAGE = "hasAuthority('payment:manage')";
    public static final String ROOM_READ = "hasAuthority('room:read')";
    public static final String ROOM_CREATE = "hasAuthority('room:create')";
    public static final String ROOM_UPDATE = "hasAuthority('room:update')";
    public static final String ROOM_DELETE = "hasAuthority('room:delete')";
    public static final String SHIFT_MANAGE = "hasAuthority('shift:manage')";
    public static final String SHIFT_READ_OWN = "hasAuthority('shift:read_own')";
    public static final String SHIFT_UPDATE_OWN = "hasAuthority('shift:update_own')";
    public static final String RBAC_READ = "hasAuthority('rbac:read')";
    public static final String RBAC_MANAGE = "hasAuthority('rbac:manage')";
    public static final String STAFF_MANAGE = "hasAuthority('staff:manage')";
    public static final String PRICING_MANAGE = "hasAuthority('pricing:manage')";
    public static final String POLICY_MANAGE = "hasAuthority('policy:manage')";
    public static final String POLICY_READ_ACTIVE =
            "hasAnyAuthority('booking:create', 'policy:manage', 'room:create', 'room:update')";
    public static final String POLICY_USE_FOR_BOOKING =
            "hasAnyAuthority('booking:create', 'policy:manage')";
    public static final String BOOKING_CANCEL =
            "hasAnyAuthority('booking:cancel_own', 'booking:cancel_any')";
    public static final String BOOKING_CHECK_IN = "hasAuthority('booking:check_in')";
    public static final String BOOKING_CHECK_OUT = "hasAuthority('booking:check_out')";
    public static final String INVOICE_ISSUE = "hasAuthority('invoice:issue')";
    public static final String INVOICE_VOID = "hasAuthority('invoice:void')";
    public static final String BOOKING_ASSIGN_ROOM = "hasAuthority('booking:assign_room')";
    public static final String BOOKING_GUEST_MANAGE = "hasAuthority('booking:assign_room')";
    public static final String GUEST_READ_ID = "hasAuthority('guest:read_id')";
    public static final String SETTINGS_MANAGE = "hasAuthority('settings:manage')";
    public static final String REFUND_APPROVE = "hasAuthority('refund:approve')";
    public static final String REFUND_REQUEST =
            "hasAnyAuthority('booking:cancel_own', 'refund:approve')";
    public static final String REVENUE_READ = "hasAuthority('revenue:read')";
    public static final String DASHBOARD_READ = "hasAuthority('dashboard:read')";
    public static final String REVIEW_CREATE = "hasAuthority('review:create')";
    public static final String REVIEW_MODERATE = "hasAuthority('review:moderate')";
    public static final String REVIEW_REPLY = "hasAuthority('review:reply')";

    private PermissionExpressions() {
    }
}
