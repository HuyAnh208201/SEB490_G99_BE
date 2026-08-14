package base.api.feature.customer.service;

import base.api.feature.customer.dto.CustomerPromotionDtos;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CustomerPromotionService {

    private final CampaignRepository repository;
    private final ObjectMapper objectMapper;

    public CustomerPromotionService(CampaignRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerPromotionDtos.PromotionResponse> listActive() {
        return repository.findLiveByStatus(CampaignStatus.ACTIVE, LocalDateTime.now())
                .stream().map(this::toResponse).toList();
    }

    private CustomerPromotionDtos.PromotionResponse toResponse(CampaignModel campaign) {
        Map<String, Object> conditions = parseConditions(campaign.getConditions());
        CustomerPromotionDtos.PromotionResponse response = new CustomerPromotionDtos.PromotionResponse();
        response.setId(campaign.getId());
        response.setName(campaign.getName());
        response.setType(campaign.getType() == null ? null : campaign.getType().name());
        response.setDiscountValue(campaign.getDiscountValue());
        response.setDiscountLabel(formatDiscount(response.getType(), campaign.getDiscountValue(), conditions));
        response.setConditions(conditions);
        response.setScope(campaign.getScope() == null ? null : campaign.getScope().name());
        response.setStartAt(campaign.getStartAt());
        response.setEndAt(campaign.getEndAt());
        response.setPriority(campaign.getPriority());
        return response;
    }

    private Map<String, Object> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String formatDiscount(String type, BigDecimal value, Map<String, Object> conditions) {
        if (type == null) {
            return value == null ? "-" : value.toPlainString();
        }
        return switch (type) {
            case "PERCENT" -> decimal(value) + "% OFF";
            case "FIXED_AMOUNT" -> vnd(value) + " OFF";
            case "BUY_X_GET_Y" -> buyXGetY(value, conditions);
            default -> value == null ? "-" : value.toPlainString();
        };
    }

    private String buyXGetY(BigDecimal value, Map<String, Object> conditions) {
        Object buy = conditions.containsKey("buyQuantity")
                ? conditions.get("buyQuantity") : conditions.get("buyQty");
        Object get = conditions.containsKey("getQuantity")
                ? conditions.get("getQuantity") : conditions.get("getQty");
        return buy != null && get != null
                ? "Buy " + buy + " Get " + get
                : value == null ? "Special offer" : "Value: " + decimal(value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String vnd(BigDecimal value) {
        NumberFormat format = NumberFormat.getInstance(new Locale("vi", "VN"));
        format.setMaximumFractionDigits(0);
        return format.format(value == null ? BigDecimal.ZERO : value) + " ₫";
    }
}
