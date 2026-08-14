package base.api.feature.customer.service;

import base.api.feature.customer.dto.CustomerPromotionDtos;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignScope;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPromotionServiceTest {

    @Mock
    private CampaignRepository repository;

    @Test
    void mapsAllMobileDiscountLabelsAndMalformedConditions() {
        CampaignModel percent = campaign(1L, CampaignType.PERCENT, "10.00", "{}");
        CampaignModel fixed = campaign(2L, CampaignType.FIXED_AMOUNT, "50000", "not-json");
        CampaignModel buy = campaign(3L, CampaignType.BUY_X_GET_Y, "0",
                "{\"buyQuantity\":2,\"getQuantity\":1}");
        when(repository.findLiveByStatus(any(CampaignStatus.class), any(LocalDateTime.class)))
                .thenReturn(List.of(percent, fixed, buy));
        CustomerPromotionService service = new CustomerPromotionService(repository, new ObjectMapper());

        List<CustomerPromotionDtos.PromotionResponse> result = service.listActive();

        assertEquals("10% OFF", result.get(0).getDiscountLabel());
        assertEquals("50.000 ₫ OFF", result.get(1).getDiscountLabel());
        assertEquals("Buy 2 Get 1", result.get(2).getDiscountLabel());
        assertEquals("CHAIN", result.get(0).getScope());
        assertEquals(0, result.get(1).getConditions().size());
    }

    private CampaignModel campaign(
            Long id,
            CampaignType type,
            String value,
            String conditions) {
        CampaignModel campaign = new CampaignModel();
        campaign.setId(id);
        campaign.setName("Campaign " + id);
        campaign.setType(type);
        campaign.setDiscountValue(new BigDecimal(value));
        campaign.setConditions(conditions);
        campaign.setScope(CampaignScope.CHAIN);
        campaign.setPriority(id.intValue());
        campaign.setStartAt(LocalDateTime.now().minusDays(1));
        campaign.setEndAt(LocalDateTime.now().plusDays(1));
        campaign.setStatus(CampaignStatus.ACTIVE);
        return campaign;
    }
}
