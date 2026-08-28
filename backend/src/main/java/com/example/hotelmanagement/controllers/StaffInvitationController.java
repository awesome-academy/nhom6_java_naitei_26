package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.auth.AuthMessageResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffInvitationAcceptRequest;
import com.example.hotelmanagement.services.StaffProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/staff-invitation")
@RequiredArgsConstructor
public class StaffInvitationController {

    private final StaffProfileService staffProfileService;

    @PostMapping("/accept")
    public AuthMessageResponse accept(@Valid @RequestBody StaffInvitationAcceptRequest request) {
        staffProfileService.acceptStaffInvitation(request);
        return new AuthMessageResponse("Kích hoạt tài khoản Staff thành công. Bạn có thể đăng nhập bằng mật khẩu trong email invitation.");
    }
}
