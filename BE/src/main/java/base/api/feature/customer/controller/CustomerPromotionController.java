package base.api.feature.customer.controller;

import base.api.feature.customer.dto.CustomerPromotionDtos;
import base.api.feature.customer.service.CustomerPromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/promotions")
public class CustomerPromotionController {

    private final CustomerPromotionService service;

    public CustomerPromotionController(CustomerPromotionService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public List<CustomerPromotionDtos.PromotionResponse> active() {
        return service.listActive();
    }
}
