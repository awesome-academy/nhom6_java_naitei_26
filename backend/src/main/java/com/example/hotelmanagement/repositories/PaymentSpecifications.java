package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> withFilters(
            String booking,
            Collection<PaymentStatus> statuses,
            PaymentMethod method,
            LocalDate from,
            LocalDate to,
            ZoneId zoneId
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Payment, com.example.hotelmanagement.entity.Booking> bookingJoin =
                    root.join("booking", JoinType.INNER);

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (method != null) {
                predicates.add(criteriaBuilder.equal(root.get("method"), method));
            }
            if (booking != null && !booking.isBlank()) {
                String pattern = "%" + booking.strip().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("paymentCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingJoin.get("publicId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingJoin.get("bookingCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingJoin.get("contactName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingJoin.get("contactEmail")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingJoin.get("contactPhone")), pattern)
                ));
            }
            if (from != null) {
                OffsetDateTime start = from.atStartOfDay(zoneId).toOffsetDateTime();
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (to != null) {
                OffsetDateTime endExclusive = to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), endExclusive));
            }

            query.orderBy(
                    criteriaBuilder.desc(root.get("createdAt")),
                    criteriaBuilder.desc(root.get("id"))
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
