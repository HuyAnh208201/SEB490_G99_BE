package base.api.feature.admin.dto.response;

import java.util.List;

/** System-administration home — access, coverage, attention items. */
public record AdminDashboardResponse(
        long activeAccounts,
        long lockedAccounts,
        long activeBranches,
        long suspendedBranches,
        long usersMissingBranch,
        long branchesWithoutManager,
        List<RoleCount> roleDistribution,
        List<BranchCoverageRow> branchCoverage,
        List<AttentionItem> attentionItems
) {
    public record RoleCount(String role, long count) {
    }

    public record BranchCoverageRow(
            Long branchId,
            String branchName,
            String status,
            boolean hasBranchManager,
            long staffCount,
            String managerName
    ) {
    }

    public record AttentionItem(String type, String label, String href) {
    }
}
