package base.api.feature.report.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.report.repository.ReportOrderRepository;
import base.api.feature.report.repository.ReportShiftSessionRepository;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ranh giới bảo mật của báo cáo: BRANCH_MANAGER chỉ được xem chi nhánh mình (branchId
 * client gửi bị bỏ qua), ADMIN/DIRECTOR dùng branchId truyền lên (null = toàn hệ thống).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportScopeTest {

    private static final Long BM_BRANCH = 10L;
    private static final Long OTHER_BRANCH = 999L;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 24);

    @Mock private ReportOrderRepository reportOrderRepository;
    @Mock private ReportShiftSessionRepository reportShiftSessionRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private IUserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ReportServiceImpl service;

    // =========================================================================
    // (1) BM: every report is forced to the BM branch; ignore client branchId
    // =========================================================================
    @Test
    void branchManagerScopeIsForcedToOwnBranchIgnoringParam() {
        asBranchManager(BM_BRANCH);
        stubEmptyRepositories();

        // BM client tries branch 999 — must be ignored and forced to 10.
        service.getRevenue("shift", FROM, TO, OTHER_BRANCH);
        service.getInvoices(FROM, TO, OTHER_BRANCH);
        service.getCashDiscrepancies(FROM, TO, OTHER_BRANCH);
        service.getPointTransactions(FROM, TO, OTHER_BRANCH);

        // All repositories receive branchId 10 (BM), not 999 (param).
        verify(reportOrderRepository).revenueByShift(eq(BM_BRANCH), any(), any());
        verify(reportOrderRepository).findInvoices(eq(BM_BRANCH), any(), any(), any());
        verify(reportShiftSessionRepository).findDiscrepancies(
                eq(UserRole.CASHIER),
                eq(List.of(ShiftSessionStatus.COMPLETED, ShiftSessionStatus.APPROVED)),
                eq(BM_BRANCH),
                any(),
                any(),
                any());
        verify(pointTransactionRepository).findHistory(eq(BM_BRANCH), any(), any(), any());
    }

    // =========================================================================
    // (2) Director: uses the client-provided branchId
    // =========================================================================
    @Test
    void directorScopeUsesRequestedBranch() {
        asDirector();
        stubEmptyRepositories();

        service.getRevenue("shift", FROM, TO, OTHER_BRANCH);
        service.getInvoices(FROM, TO, OTHER_BRANCH);

        verify(reportOrderRepository).revenueByShift(eq(OTHER_BRANCH), any(), any());
        verify(reportOrderRepository).findInvoices(eq(OTHER_BRANCH), any(), any(), any());
    }

    // =========================================================================
    // (2b) Director without branchId -> system-wide (branchId = null)
    // =========================================================================
    @Test
    void directorWithoutBranchParamSeesAllBranches() {
        asDirector();
        stubEmptyRepositories();

        service.getInvoices(FROM, TO, null);
        service.getPointTransactions(FROM, TO, null);

        verify(reportOrderRepository).findInvoices(isNull(), any(), any(), any());
        verify(pointTransactionRepository).findHistory(isNull(), any(), any(), any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void asBranchManager(Long branchId) {
        UserModel manager = new UserModel();
        manager.setId(5L);
        manager.setBranchId(branchId);
        manager.setRole(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
    }

    private void asDirector() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.DIRECTOR);
    }

    private void stubEmptyRepositories() {
        when(reportOrderRepository.revenueByShift(any(), any(), any())).thenReturn(List.of());
        when(reportOrderRepository.revenueByEmployee(any(), any(), any())).thenReturn(List.of());
        when(reportOrderRepository.revenueByBranch(any(), any(), any())).thenReturn(List.of());
        when(reportOrderRepository.findInvoices(any(), any(), any(), any())).thenReturn(List.of());
        when(reportShiftSessionRepository.findDiscrepancies(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(pointTransactionRepository.findHistory(any(), any(), any(), any())).thenReturn(List.of());
    }
}
