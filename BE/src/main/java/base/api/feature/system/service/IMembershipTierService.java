package base.api.feature.system.service;

import base.api.feature.system.dto.request.UpdateMembershipTierRequest;
import base.api.feature.system.dto.response.MembershipTierResponse;

import java.util.List;

public interface IMembershipTierService {

    List<MembershipTierResponse> listTiers();

    MembershipTierResponse updateTier(Long id, UpdateMembershipTierRequest request);
}
