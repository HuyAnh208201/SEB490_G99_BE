package base.api.feature.promotion.job;

import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CampaignExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CampaignExpiryJob.class);

    @Autowired
    private CampaignRepository campaignRepository;

    @Scheduled(fixedDelayString = "${campaign.expiry-check-ms:900000}")
    @Transactional
    public void deactivateExpiredCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        List<CampaignModel> active = campaignRepository.findByStatus(CampaignStatus.ACTIVE);
        int count = 0;
        for (CampaignModel campaign : active) {
            if (campaign.getEndAt() != null && campaign.getEndAt().isBefore(now)) {
                campaign.setStatus(CampaignStatus.DEACTIVATED);
                campaignRepository.save(campaign);
                count++;
            }
        }
        if (count > 0) {
            log.info("Auto-deactivated {} expired promotion(s)", count);
        }
    }
}
