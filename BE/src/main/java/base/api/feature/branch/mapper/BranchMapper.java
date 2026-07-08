package base.api.feature.branch.mapper;

import base.api.feature.branch.dto.response.BranchResponse;
import base.api.feature.branch.dto.response.UserResponse;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.UserModel;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {

    public BranchResponse toListResponse(BranchModel branch, String managerName) {
        BranchResponse response = new BranchResponse();
        response.setId(branch.getId());
        response.setName(branch.getName());
        response.setAddress(branch.getAddress());
        response.setPhone(branch.getPhone());
        response.setOperatingHours(branch.getOperatingHours());
        response.setManagerName(managerName);
        response.setManagerId(branch.getManagerId());
        response.setStatus(branch.getStatus());
        return response;
    }

    public BranchResponse toDetailResponse(BranchModel branch, String managerName) {
        BranchResponse response = toListResponse(branch, managerName);
        response.setManagerId(branch.getManagerId());
        response.setCreatedAt(branch.getCreatedAt());
        response.setUpdatedAt(branch.getUpdatedAt());
        return response;
    }

    public UserResponse toUserResponse(UserModel user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole() != null ? user.getRole().name() : null);
        response.setBranchId(user.getBranchId());
        response.setStatus(user.getStatus());
        return response;
    }
}
