package base.api.feature.customer.controller;

import base.api.feature.customer.dto.CustomerDtos;
import base.api.feature.customer.service.CustomerAccountService;
import base.api.shared.entity.UserModel;
import base.api.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerAccountService service;
    private final CurrentUserProvider currentUserProvider;

    public CustomerController(CustomerAccountService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    public CustomerDtos.ProfileResponse me() {
        return service.getProfile(currentUser());
    }

    @PutMapping("/me")
    public CustomerDtos.ProfileResponse updateMe(
            @Valid @RequestBody CustomerDtos.UpdateProfileRequest request) {
        return service.updateProfile(currentUser(), request);
    }

    @GetMapping("/me/qr")
    public CustomerDtos.QrResponse qr() {
        return service.qr(currentUser());
    }

    @GetMapping("/points/history")
    public CustomerDtos.PageResponse<CustomerDtos.PointHistoryItem> pointHistory(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pointHistory(currentUser().getId(), type, page, size);
    }

    @GetMapping("/loyalty/config")
    public CustomerDtos.LoyaltyConfigResponse loyaltyConfig() {
        return service.loyaltyConfig();
    }

    @GetMapping("/tiers")
    public List<CustomerDtos.TierResponse> tiers() {
        return service.listTiers(currentUser());
    }

    private UserModel currentUser() {
        return currentUserProvider.getCurrentUserOrThrow();
    }
}
