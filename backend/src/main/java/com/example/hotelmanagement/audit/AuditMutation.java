package com.example.hotelmanagement.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a business mutation that must be written to {@code audit_logs}.
 *
 * <p>Use this for mutations whose verb does not start with {@code update} or {@code delete},
 * such as a booking status transition. Conventional update/delete service methods are recorded
 * automatically by {@link ServiceMutationAuditAspect}.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditMutation {

    String action();

    String entityType();

    /** Zero-based argument index, or -1 when the actor comes from the authenticated principal. */
    int actorUserIdArgumentIndex() default -1;

    /** Zero-based argument index, or -1 when no numeric entity id is available. */
    int entityIdArgumentIndex() default -1;
}
