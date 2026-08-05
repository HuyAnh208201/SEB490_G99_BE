package base.api.feature.branchmanager.service;

import base.api.feature.branchmanager.dto.response.BranchManagerDashboardResponse;

import java.time.LocalDate;

public interface IBranchManagerDashboardService {

    BranchManagerDashboardResponse getDashboard(LocalDate from, LocalDate to);
}
