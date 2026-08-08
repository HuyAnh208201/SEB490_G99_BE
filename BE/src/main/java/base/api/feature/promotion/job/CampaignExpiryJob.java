package base.api.feature.promotion.job;

import base.api.feature.promotion.service.CampaignExpiryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignExpiryJob {

    @Autowired
    private CampaignExpiryService campaignExpiryService;

    @Scheduled(fixedDelayString = "${campaign.expiry-check-ms:60000}")
    public void deactivateExpiredCampaigns() {
        campaignExpiryService.deactivateExpiredCampaigns();
    }
}
