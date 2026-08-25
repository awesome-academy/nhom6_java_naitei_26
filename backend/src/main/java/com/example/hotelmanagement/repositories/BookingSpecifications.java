package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.dto.booking.BookingListFilterRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> withFilters(BookingListFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by statuses
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }

            // Filter by source code
            if (filter.sourceCode() != null && !filter.sourceCode().isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("source").get("code")),
                        filter.sourceCode().toUpperCase()
                ));
            }

            // Search by booking code, contact name, contact phone
            if (filter.search() != null && !filter.search().isBlank()) {
                String searchPattern = "%" + filter.search().toLowerCase() + "%";
                Predicate searchPredicate = criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("bookingCode")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactName")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactPhone")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactEmail")), searchPattern)
                );
                predicates.add(searchPredicate);
            }

            // Filter by check-in date range (earliest check-in across all rooms)
            if (filter.checkInFrom() != null || filter.checkInTo() != null) {
                Subquery<java.time.LocalDate> checkInSubquery = query.subquery(java.time.LocalDate.class);
                Root<Booking> correlatedBooking = checkInSubquery.correlate(root);
                Join<Booking, BookingRoom> rooms = correlatedBooking.join("bookingRooms", JoinType.INNER);
                checkInSubquery.select(criteriaBuilder.function(
                        "MIN",
                        java.time.LocalDate.class,
                        rooms.get("checkInDate")
                ));

                if (filter.checkInFrom() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(checkInSubquery, filter.checkInFrom()));
                }
                if (filter.checkInTo() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(checkInSubquery, filter.checkInTo()));
                }
            }

            // Filter by check-out date range (latest check-out across all rooms)
            if (filter.checkOutFrom() != null || filter.checkOutTo() != null) {
                Subquery<java.time.LocalDate> checkOutSubquery = query.subquery(java.time.LocalDate.class);
                Root<Booking> correlatedBooking = checkOutSubquery.correlate(root);
                Join<Booking, BookingRoom> rooms = correlatedBooking.join("bookingRooms", JoinType.INNER);
                checkOutSubquery.select(criteriaBuilder.function(
                        "MAX",
                        java.time.LocalDate.class,
                        rooms.get("checkOutDate")
                ));

                if (filter.checkOutFrom() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(checkOutSubquery, filter.checkOutFrom()));
                }
                if (filter.checkOutTo() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(checkOutSubquery, filter.checkOutTo()));
                }
            }

            // Order by created date descending
            query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
