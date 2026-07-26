package base.api.feature.branch.service;

import base.api.feature.branch.dto.request.AssignStaffRequest;
import base.api.feature.branch.dto.request.SendBranchSuspendCodeRequest;
import base.api.feature.branch.dto.request.CreateBranchManagerRequest;
import base.api.feature.branch.dto.request.CreateBranchRequest;
import base.api.feature.branch.dto.request.CreateCashierRequest;
import base.api.feature.branch.dto.request.CreateInventoryStaffRequest;
import base.api.feature.branch.dto.request.UpdateBranchRequest;
import base.api.feature.branch.dto.request.UpdateBranchStatusRequest;
import base.api.feature.branch.dto.response.BranchResponse;
import base.api.feature.branch.dto.response.UserResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IBranchService {

    BranchResponse createBranch(CreateBranchRequest request);

    BranchResponse updateBranch(Long id, UpdateBranchRequest request);

    BranchResponse getBranch(Long id);

    List<BranchResponse> getAllBranches();

    Page<BranchResponse> getBranchPage(PageRequestDTO pageRequest, String status);

    BranchResponse suspendBranch(Long id, UpdateBranchStatusRequest request);

    UserResponse createBranchManager(Long branchId, CreateBranchManagerRequest request);

    UserResponse createInventoryStaff(CreateInventoryStaffRequest request);

    UserResponse createCashier(CreateCashierRequest request);

    UserResponse assignStaffToBranch(Long branchId, AssignStaffRequest request);

    void sendBranchSuspendCode(Long branchId, SendBranchSuspendCodeRequest request);
}
