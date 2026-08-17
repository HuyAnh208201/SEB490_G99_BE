package base.api.feature.customer.controller;

import base.api.feature.customer.dto.CustomerAuthDtos;
import base.api.feature.customer.service.CustomerAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/auth")
public class CustomerAuthController {

    private final CustomerAuthService service;

    public CustomerAuthController(CustomerAuthService service) {
        this.service = service;
    }

    @PostMapping("/register/request-otp")
    public CustomerAuthDtos.MessageResponse requestRegisterOtp(
            @Valid @RequestBody CustomerAuthDtos.RegisterRequestOtp request) {
        return service.requestRegisterOtp(request);
    }

    @PostMapping("/register/verify")
    public CustomerAuthDtos.AuthResponse verifyRegister(
            @Valid @RequestBody CustomerAuthDtos.RegisterVerify request) {
        return service.verifyRegister(request);
    }

    @PostMapping("/login")
    public CustomerAuthDtos.AuthResponse login(@Valid @RequestBody CustomerAuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/forgot/request-otp")
    public CustomerAuthDtos.MessageResponse requestForgotOtp(
            @Valid @RequestBody CustomerAuthDtos.ForgotRequestOtp request) {
        return service.requestForgotOtp(request);
    }

    @PostMapping("/forgot/verify")
    public CustomerAuthDtos.MessageResponse verifyForgot(
            @Valid @RequestBody CustomerAuthDtos.ForgotVerify request) {
        return service.verifyForgot(request);
    }
}
