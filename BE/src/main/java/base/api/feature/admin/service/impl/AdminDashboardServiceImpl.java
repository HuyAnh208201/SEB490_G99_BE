package base.api.feature.admin.service.impl;

import base.api.feature.admin.dto.response.AdminDashboardResponse;
import base.api.feature.admin.dto.response.AdminDashboardResponse.AttentionItem;
import base.api.feature.admin.dto.response.AdminDashboardResponse.BranchCoverageRow;
import base.api.feature.admin.dto.response.AdminDashboardResponse.RoleCount;
import base.api.feature.admin.service.IAdminDashboardService;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminDashboardServiceImpl implements IAdminDashboardService {

    private static final List<String> BRANCH_REQUIRED_ROLES = List.of(
            UserRole.BRANCH_MANAGER.name(),
            UserRole.INVENTORY_STAFF.name(),
            UserRole.CASHIER.name()
    );

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Override
    public AdminDashboardResponse getDashboard() {
        long activeAccounts = userRepository.countWebUsersByStatus("active");
        long lockedAccounts = userRepository.countWebUsersByStatus("locked");
        long activeBranches = branchRepository.countByStatusIgnoreCase("ACTIVE");
        long suspendedBranches = branchRepository.countByStatusIgnoreCase("SUSPENDED");

        List<UserModel> missingBranchUsers = userRepository.findMissingBranch(BRANCH_REQUIRED_ROLES);
        List<RoleCount> roleDistribution = userRepository.countGroupedByRoleExcludingCustomer().stream()
                .map(row -> new RoleCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .sorted(Comparator.comparing(RoleCount::role))
                .toList();

        List<BranchModel> branches = branchRepository.findAll().stream()
                .sorted(Comparator.comparing(BranchModel::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<BranchCoverageRow> coverage = new ArrayList<>();
        List<AttentionItem> attention = new ArrayList<>();
        long branchesWithoutManager = 0;

        for (BranchModel branch : branches) {
            List<UserModel> managers = userRepository.findActiveBranchManagers(branch.getId());
            boolean hasManager = !managers.isEmpty();
            if (!hasManager) {
                branchesWithoutManager++;
                attention.add(new AttentionItem(
                        "BRANCH_NO_MANAGER",
                        "Branch \"" + branch.getName() + "\" has no active branch manager",
                        "/users"
                ));
            }
            String managerName = managers.isEmpty()
                    ? null
                    : managers.get(0).getFullName();
            coverage.add(new BranchCoverageRow(
                    branch.getId(),
                    branch.getName(),
                    branch.getStatus(),
                    hasManager,
                    userRepository.countStaffByBranchId(branch.getId()),
                    managerName
            ));
        }

        for (UserModel user : missingBranchUsers) {
            attention.add(new AttentionItem(
                    "USER_MISSING_BRANCH",
                    user.getFullName() + " (" + user.getRole().name() + ") has no branch assigned",
                    "/users"
            ));
        }

        if (lockedAccounts > 0) {
            attention.add(0, new AttentionItem(
                    "LOCKED_ACCOUNTS",
                    lockedAccounts + " locked account(s) need review",
                    "/users"
            ));
        }

        if (suspendedBranches > 0) {
            attention.add(new AttentionItem(
                    "SUSPENDED_BRANCHES",
                    suspendedBranches + " suspended branch(es)",
                    "/branches"
            ));
        }

        return new AdminDashboardResponse(
                activeAccounts,
                lockedAccounts,
                activeBranches,
                suspendedBranches,
                missingBranchUsers.size(),
                branchesWithoutManager,
                roleDistribution,
                coverage,
                attention
        );
    }
}
