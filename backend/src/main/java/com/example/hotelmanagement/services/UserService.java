package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.user.UserResponse;
import com.example.hotelmanagement.dto.user.UserUpdateRequest;
import com.example.hotelmanagement.dto.user.CustomerBookingResponse;
import com.example.hotelmanagement.dto.user.CustomerDetailResponse;
import com.example.hotelmanagement.dto.user.CustomerListItemResponse;
import com.example.hotelmanagement.dto.user.CustomerListResponse;
import com.example.hotelmanagement.dto.user.CustomerStatusUpdateRequest;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;
import com.example.hotelmanagement.entity.AuditLog;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Validated
@Transactional
public class UserService {

    public static final int CUSTOMER_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final Clock clock;
    private final CustomerProfileRepository customerProfileRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogRepository auditLogRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(
            UserRepository userRepository,
            Clock clock,
            CustomerProfileRepository customerProfileRepository,
            BookingRepository bookingRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.customerProfileRepository = customerProfileRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public UserService(UserRepository userRepository, Clock clock) {
        this(userRepository, clock, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()
            .stream()
            .map(this::mapUserResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String publicId) {
        return mapUserResponse(getExistingUser(publicId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerListResponse getCustomers(UserStatus status, String search, int page) {
        if (page < 0) {
            throw new BusinessValidationException("Page must be zero or greater");
        }

        String normalizedSearch = search == null ? "" : search.strip();
        Page<User> users = userRepository.findCustomerUsers(
                status,
                normalizedSearch,
                PageRequest.of(page, CUSTOMER_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<CustomerListItemResponse> items = users.getContent().stream()
                .map(this::mapCustomerListItem)
                .toList();

        return new CustomerListResponse(
                items,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerDetailResponse getCustomer(String publicId) {
        User user = getExistingCustomer(publicId);
        CustomerProfile profile = customerProfileRepository.findByUser_Id(user.getId())
                .orElse(null);
        return new CustomerDetailResponse(
                mapUserResponse(user),
                profile == null ? null : mapCustomerProfileResponse(profile)
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerBookingResponse> getCustomerBookings(String publicId) {
        User user = getExistingCustomer(publicId);
        return bookingRepository.findAllByCustomerProfile_User_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::mapCustomerBooking)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateCustomerStatus(
            String publicId,
            @Valid CustomerStatusUpdateRequest request,
            Long actorUserId
    ) {
        User user = getExistingCustomer(publicId);
        UserStatus previousStatus = user.getStatus();
        UserStatus targetStatus = request.status();
        if (!isCustomerManagedStatus(previousStatus) || !isCustomerManagedStatus(targetStatus)) {
            throw new BusinessValidationException(
                    "Customer status can only transition between ACTIVE and DEACTIVATED"
            );
        }

        user.setStatus(targetStatus);
        User savedUser = userRepository.save(user);
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(actorUserId)
                .action("CUSTOMER_STATUS_CHANGED")
                .entityType("user")
                .entityId(savedUser.getId())
                .beforeData("{\"status\":\"" + previousStatus.name() + "\"}")
                .afterData("{\"status\":\"" + targetStatus.name() + "\"}")
                .build());
        return mapUserResponse(savedUser);
    }

    public UserResponse updateUser(String publicId, @Valid UserUpdateRequest request) {
        User user = getExistingUser(publicId);

        if (request.fullName() != null) {
            user.setFullName(normalizeRequiredText(request.fullName(), "Full name"));
        }
        if (request.phone() != null) {
            String normalizedPhone = normalizeOptionalText(request.phone());
            ensurePhoneIsAvailable(normalizedPhone, user);
            user.setPhone(normalizedPhone);
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalizeOptionalText(request.avatarUrl()));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        return mapUserResponse(userRepository.save(user));
    }

    public void deleteUser(String publicId) {
        User user = getExistingUser(publicId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeletedAt(OffsetDateTime.now(clock));
        userRepository.save(user);
    }

    private User getExistingUser(String publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ResourceNotFoundException("User", publicId));
    }

    private User getExistingCustomer(String publicId) {
        User user = getExistingUser(publicId);
        boolean isCustomer = user.getUserRoles().stream()
                .anyMatch(userRole -> "CUSTOMER".equals(userRole.getRole().getCode()));
        if (!isCustomer) {
            throw new ResourceNotFoundException("Customer", publicId);
        }
        return user;
    }

    private CustomerListItemResponse mapCustomerListItem(User user) {
        return new CustomerListItemResponse(
                user.getPublicId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                bookingRepository.countByCustomerProfile_User_Id(user.getId()),
                user.getCreatedAt()
        );
    }

    private CustomerBookingResponse mapCustomerBooking(Booking booking) {
        List<BookingRoom> bookingRooms = booking.getBookingRooms().stream().toList();
        LocalDate checkIn = bookingRooms.stream()
                .map(BookingRoom::getCheckInDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate checkOut = bookingRooms.stream()
                .map(BookingRoom::getCheckOutDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        int nights = bookingRooms.stream()
                .filter(room -> room.getCheckInDate() != null && room.getCheckOutDate() != null)
                .mapToInt(room -> (int) ChronoUnit.DAYS.between(room.getCheckInDate(), room.getCheckOutDate()))
                .sum();

        return new CustomerBookingResponse(
                booking.getBookingCode(),
                checkIn,
                checkOut,
                nights,
                bookingRooms.size(),
                safeInt(booking.getAdults()) + safeInt(booking.getChildren()),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getPaymentStatus()
        );
    }

    private CustomerProfileResponse mapCustomerProfileResponse(CustomerProfile profile) {
        User user = profile.getUser();
        return new CustomerProfileResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getNationality(),
                profile.getProvince(),
                profile.getAddressLine(),
                profile.getCountry(),
                user.getAvatarUrl(),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt(),
                profile.getLoyaltyPoints(),
                profile.getTotalStays(),
                profile.getNotes(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private boolean isCustomerManagedStatus(UserStatus status) {
        return status == UserStatus.ACTIVE || status == UserStatus.DEACTIVATED;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void ensurePhoneIsAvailable(String phone, User user) {
        if (phone == null || phone.equals(user.getPhone())) {
            return;
        }
        userRepository.findByPhoneAndDeletedAtIsNull(phone)
            .filter(existingUser -> !existingUser.getPublicId().equals(user.getPublicId()))
            .ifPresent(existingUser -> {
                throw new DuplicateResourceException("User", "phone", phone);
            });
    }

    private UserResponse mapUserResponse(User user) {
        return new UserResponse(
            user.getPublicId(),
            user.getEmail(),
            user.getEmailVerifiedAt(),
            user.getPhone(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getStatus(),
            collectRoles(user),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private Set<String> collectRoles(User user) {
        Set<String> roles = new LinkedHashSet<>();
        user.getUserRoles().forEach(userRole -> roles.add(userRole.getRole().getCode()));
        return Set.copyOf(roles);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = value.strip();
        if (normalizedValue.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return normalizedValue;
    }

    private String normalizeOptionalText(String value) {
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }
}
