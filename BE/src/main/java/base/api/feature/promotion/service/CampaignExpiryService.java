package base.api.feature.promotion.service;

import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deactivates ACTIVE campaigns whose endAt is in the past.
 * Used by the scheduled job and lazily on campaign list/get so admin status matches mobile.
 */
@Service
public class CampaignExpiryService {

    private static final Logger log = LoggerFactory.getLogger(CampaignExpiryService.class);

    @Autowired
    private CampaignRepository campaignRepository;

    @Transactional
    public int deactivateExpiredCampaigns() {
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
        return count;
    }
}
