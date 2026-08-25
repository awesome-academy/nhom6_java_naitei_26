package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.RoomTypeCancellationPolicy;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CancellationPolicyRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class BookingOptionResolverService {

    private static final String NON_REFUND_POLICY_CODE = "NON_REFUND";

    private final RoomTypeRepository roomTypeRepository;
    private final CancellationPolicyRepository cancellationPolicyRepository;

    public BookingOptionResolverService(
            RoomTypeRepository roomTypeRepository,
            CancellationPolicyRepository cancellationPolicyRepository
    ) {
        this.roomTypeRepository = roomTypeRepository;
        this.cancellationPolicyRepository = cancellationPolicyRepository;
    }

    public BookingOptionSelection resolve(
            String roomTypeCode,
            BookingPaymentOption paymentOption,
            String cancellationPolicyCode
    ) {
        RoomType roomType = roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(normalizeCode(roomTypeCode))
                .orElseThrow(() -> new ResourceNotFoundException("Room type", roomTypeCode));
        if (!Boolean.TRUE.equals(roomType.getIsActive())) {
            throw new BusinessValidationException("Only active room types can be booked");
        }

        BookingPaymentOption normalizedPaymentOption = paymentOption == null
                ? BookingPaymentOption.ONLINE
                : paymentOption;
        String normalizedPolicyCode = normalizeCode(cancellationPolicyCode);

        if (normalizedPaymentOption == BookingPaymentOption.PAY_AT_HOTEL) {
            return resolvePayAtHotelOption(roomType, normalizedPolicyCode);
        }
        return resolveOnlineOption(roomType, normalizedPolicyCode);
    }

    private BookingOptionSelection resolveOnlineOption(RoomType roomType, String cancellationPolicyCode) {
        RoomTypeCancellationPolicy option = roomType.getCancellationPolicyOptions().stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .filter(candidate -> candidate.getCancellationPolicy() != null)
                .filter(candidate -> cancellationPolicyCode.equalsIgnoreCase(
                        candidate.getCancellationPolicy().getCode()
                ))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "Cancellation policy is not enabled for this room type"
                ));
        CancellationPolicy policy = option.getCancellationPolicy();
        validateActivePolicy(policy, cancellationPolicyCode);
        return new BookingOptionSelection(
                roomType,
                BookingPaymentOption.ONLINE,
                policy,
                normalizePercent(policy.getPriceAdjustmentPercent())
        );
    }

    private BookingOptionSelection resolvePayAtHotelOption(RoomType roomType, String cancellationPolicyCode) {
        if (!Boolean.TRUE.equals(roomType.getPayAtHotelEnabled())) {
            throw new BusinessValidationException("Pay at hotel is not enabled for this room type");
        }
        if (!NON_REFUND_POLICY_CODE.equalsIgnoreCase(cancellationPolicyCode)) {
            throw new BusinessValidationException("Pay at hotel must use non-refundable policy");
        }
        CancellationPolicy policy = cancellationPolicyRepository
                .findByCodeIgnoreCaseAndIsActiveTrue(NON_REFUND_POLICY_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Active cancellation policy", NON_REFUND_POLICY_CODE));
        return new BookingOptionSelection(
                roomType,
                BookingPaymentOption.PAY_AT_HOTEL,
                policy,
                normalizePercent(roomType.getPayAtHotelPriceAdjustmentPercent())
        );
    }

    private void validateActivePolicy(CancellationPolicy policy, String code) {
        if (policy == null || !Boolean.TRUE.equals(policy.getIsActive())) {
            throw new ResourceNotFoundException("Active cancellation policy", code);
        }
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Code cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
