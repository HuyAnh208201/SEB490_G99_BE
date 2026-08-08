package base.api.feature.promotion.service;

import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignExpiryServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignExpiryService service;

    @Test
    void deactivatesOnlyExpiredActiveCampaigns() {
        CampaignModel expired = campaign(1L, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        CampaignModel live = campaign(2L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(3));
        when(campaignRepository.findByStatus(CampaignStatus.ACTIVE)).thenReturn(List.of(expired, live));
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.deactivateExpiredCampaigns();

        assertEquals(1, count);
        assertEquals(CampaignStatus.DEACTIVATED, expired.getStatus());
        assertEquals(CampaignStatus.ACTIVE, live.getStatus());
        ArgumentCaptor<CampaignModel> captor = ArgumentCaptor.forClass(CampaignModel.class);
        verify(campaignRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        verify(campaignRepository, never()).save(live);
    }

    private static CampaignModel campaign(Long id, LocalDateTime startAt, LocalDateTime endAt) {
        CampaignModel c = new CampaignModel();
        c.setId(id);
        c.setStatus(CampaignStatus.ACTIVE);
        c.setStartAt(startAt);
        c.setEndAt(endAt);
        return c;
    }
}
