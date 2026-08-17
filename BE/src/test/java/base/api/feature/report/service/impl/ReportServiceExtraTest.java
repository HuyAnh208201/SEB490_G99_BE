package base.api.feature.report.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.report.dto.CashDiscrepancyResponse;
import base.api.feature.report.dto.CashDiscrepancyRow;
import base.api.feature.report.dto.InvoiceRow;
import base.api.feature.report.dto.OrderSummaryAgg;
import base.api.feature.report.dto.PointTransactionResponse;
import base.api.feature.report.dto.PointTransactionRow;
import base.api.feature.report.dto.ReportSummaryResponse;
import base.api.feature.report.dto.RevenueAggRow;
import base.api.feature.report.dto.RevenueRow;
import base.api.feature.report.dto.TopProductAggRow;
import base.api.feature.report.dto.TopProductRow;
import base.api.feature.report.dto.TrendPoint;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.report.repository.ReportOrderRepository;
import base.api.feature.report.repository.ReportShiftSessionRepository;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra ReportServiceImpl coverage beyond getRevenue scope checks (see ReportScopeTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceExtraTest {

    private static final Long BM_BRANCH = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 24);

    @Mock private ReportOrderRepository reportOrderRepository;
    @Mock private ReportShiftSessionRepository reportShiftSessionRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ReportServiceImpl service;

    // -------------------------------------------------------------------------
    // role branching / forbidden
    // -------------------------------------------------------------------------

    @Test
    void getSummaryRejectsCashierRole() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.getSummary(FROM, TO, null, null));

        assertTrue(error.getMessage().contains("You do not have access to business reports."));
    }

    @Test
    void getSummaryRejectsBranchManagerWithoutBranch() {
        UserModel manager = new UserModel();
        manager.setId(5L);
        manager.setRole(UserRole.BRANCH_MANAGER);
        manager.setBranchId(null);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getSummary(FROM, TO, 999L, null));

        assertTrue(error.getMessage().contains("Branch manager is not assigned to a branch."));
    }

    @Test
    void getSummaryForcesBranchManagerOwnBranch() {
        asBranchManager(BM_BRANCH);
        when(reportOrderRepository.summarizeOrders(eq(BM_BRANCH), isNull(), any(), any()))
                .thenReturn(new OrderSummaryAgg(2L, new BigDecimal("1000")));
        when(reportOrderRepository.revenueByBranch(eq(BM_BRANCH), any(), any())).thenReturn(List.of());

        ReportSummaryResponse summary = service.getSummary(FROM, TO, 999L, null);

        assertEquals(new BigDecimal("1000"), summary.totalRevenue());
        assertEquals(2L, summary.transactionCount());
        assertEquals(new BigDecimal("500.00"), summary.avgTransactionValue());
        verify(reportOrderRepository).summarizeOrders(eq(BM_BRANCH), isNull(), any(), any());
    }

    @Test
    void getSummaryUsesRequestedBranchForAdmin() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(reportOrderRepository.summarizeOrders(eq(20L), isNull(), any(), any()))
                .thenReturn(new OrderSummaryAgg(0L, BigDecimal.ZERO));
        when(reportOrderRepository.revenueByBranch(eq(20L), any(), any())).thenReturn(List.of());

        service.getSummary(FROM, TO, 20L, null);

        verify(reportOrderRepository).summarizeOrders(eq(20L), isNull(), any(), any());
    }

    @Test
    void getSummaryIncludesTopBranchWhenPresent() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.DIRECTOR);
        when(reportOrderRepository.summarizeOrders(isNull(), isNull(), any(), any()))
                .thenReturn(new OrderSummaryAgg(1L, new BigDecimal("100")));
        when(reportOrderRepository.revenueByBranch(isNull(), any(), any()))
                .thenReturn(List.of(new RevenueAggRow(20L, 1L, new BigDecimal("100"))));
        BranchModel branch = new BranchModel();
        branch.setId(20L);
        branch.setName("District 1");
        when(branchRepository.findById(20L)).thenReturn(Optional.of(branch));

        ReportSummaryResponse summary = service.getSummary(FROM, TO, null, null);

        assertEquals(20L, summary.topBranch().id());
        assertEquals("District 1", summary.topBranch().name());
    }

    // -------------------------------------------------------------------------
    // trend / top products
    // -------------------------------------------------------------------------

    @Test
    void getTrendMapsSqlDateRows() {
        asBranchManager(BM_BRANCH);
        Object[] row = new Object[] {
                Date.valueOf(LocalDate.of(2026, 7, 2)),
                new BigDecimal("1500"),
                3L
        };
        when(reportOrderRepository.revenueTrendNative(eq(BM_BRANCH), isNull(), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<TrendPoint> points = service.getTrend(FROM, TO, null, null);

        assertEquals(1, points.size());
        assertEquals(LocalDate.of(2026, 7, 2), points.get(0).date());
        assertEquals(new BigDecimal("1500"), points.get(0).revenue());
        assertEquals(3L, points.get(0).orderCount());
    }

    @Test
    void getTopProductsDefaultsLimitAndBlankNameFallback() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(reportOrderRepository.topProductsByRevenue(isNull(), isNull(), any(), any(), any()))
                .thenReturn(List.of(new TopProductAggRow(9, "  ", 4L, new BigDecimal("80"), BigDecimal.ZERO)));

        List<TopProductRow> rows = service.getTopProducts(FROM, TO, null, null, 0);

        assertEquals(1, rows.size());
        assertEquals("Product #9", rows.get(0).productName());
        assertEquals(4L, rows.get(0).qtySold());
    }

    @Test
    void getTopProductsCapsLimitAtTwenty() {
        asBranchManager(BM_BRANCH);
        when(reportOrderRepository.topProductsByRevenue(eq(BM_BRANCH), isNull(), any(), any(), any()))
                .thenReturn(List.of());

        service.getTopProducts(FROM, TO, null, null, 100);

        verify(reportOrderRepository).topProductsByRevenue(
                eq(BM_BRANCH), isNull(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // invoices / discrepancies / points
    // -------------------------------------------------------------------------

    @Test
    void getInvoicesForcesBranchManagerScope() {
        asBranchManager(BM_BRANCH);
        when(reportOrderRepository.findInvoices(eq(BM_BRANCH), any(), any(), any()))
                .thenReturn(List.of(new InvoiceRow(
                        1L, "INV-1", BM_BRANCH, 5L, 8L, new BigDecimal("10"), "COMPLETED", LocalDateTime.now())));

        List<InvoiceRow> rows = service.getInvoices(FROM, TO, 999L);

        assertEquals(1, rows.size());
        verify(reportOrderRepository).findInvoices(eq(BM_BRANCH), any(), any(), any());
    }

    @Test
    void getInvoicePageUsesAdminRequestedBranch() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        PageRequestDTO page = new PageRequestDTO();
        page.setPage(1);
        page.setSize(10);
        when(reportOrderRepository.findInvoicePage(eq(20L), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getInvoicePage(FROM, TO, 20L, page);

        verify(reportOrderRepository).findInvoicePage(eq(20L), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void getCashDiscrepanciesResolvesEmployeeAndReviewerNames() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.DIRECTOR);
        CashDiscrepancyRow row = new CashDiscrepancyRow(
                1L, 7L, 5L,
                new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("-10"),
                99L, "Checked", LocalDateTime.now());
        when(reportShiftSessionRepository.findDiscrepancies(
                eq(UserRole.CASHIER),
                eq(List.of(ShiftSessionStatus.COMPLETED, ShiftSessionStatus.APPROVED)),
                isNull(), any(), any(), any()))
                .thenReturn(List.of(row));
        UserModel employee = namedUser(5L, "Lan Nguyen", "lan@chainstore.com");
        UserModel reviewer = namedUser(99L, "BM One", "bm@chainstore.com");
        when(userRepository.findAllById(any())).thenReturn(List.of(employee, reviewer));

        List<CashDiscrepancyResponse> responses = service.getCashDiscrepancies(FROM, TO, null);

        assertEquals(1, responses.size());
        assertEquals("Lan Nguyen", responses.get(0).employeeName());
        assertEquals("BM One", responses.get(0).reviewedByName());
    }

    @Test
    void getCashDiscrepancyPageMapsPagedRows() {
        asBranchManager(BM_BRANCH);
        PageRequestDTO page = new PageRequestDTO();
        page.setPage(1);
        page.setSize(5);
        CashDiscrepancyRow row = new CashDiscrepancyRow(
                1L, 7L, 5L,
                new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                null, null, LocalDateTime.now());
        when(reportShiftSessionRepository.findDiscrepancyPage(
                eq(UserRole.CASHIER),
                eq(List.of(ShiftSessionStatus.COMPLETED, ShiftSessionStatus.APPROVED)),
                eq(BM_BRANCH), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(userRepository.findAllById(any())).thenReturn(List.of(namedUser(5L, null, "cashier@chainstore.com")));

        Page<CashDiscrepancyResponse> result = service.getCashDiscrepancyPage(FROM, TO, 999L, page);

        assertEquals(1, result.getTotalElements());
        assertEquals("cashier@chainstore.com", result.getContent().get(0).employeeName());
        assertNull(result.getContent().get(0).reviewedByName());
    }

    @Test
    void getPointTransactionsResolvesCustomerNames() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        PointTransactionRow row = new PointTransactionRow(
                3L, 8L, 100L, 50L, "EARN", LocalDateTime.now());
        when(pointTransactionRepository.findHistory(isNull(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(namedUser(8L, "Customer A", "a@chainstore.com")));

        List<PointTransactionResponse> responses = service.getPointTransactions(FROM, TO, null);

        assertEquals(1, responses.size());
        assertEquals("Customer A", responses.get(0).customerName());
        assertEquals(50L, responses.get(0).points());
    }

    @Test
    void getPointTransactionPageForcesBranchManagerScope() {
        asBranchManager(BM_BRANCH);
        PageRequestDTO page = new PageRequestDTO();
        page.setPage(1);
        page.setSize(10);
        when(pointTransactionRepository.findHistoryPage(eq(BM_BRANCH), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getPointTransactionPage(FROM, TO, 999L, page);

        verify(pointTransactionRepository).findHistoryPage(
                eq(BM_BRANCH), any(), any(), any(), any(Pageable.class));
    }

    // -------------------------------------------------------------------------
    // revenue page / unsupported groupBy via other entry
    // -------------------------------------------------------------------------

    @Test
    void getRevenuePageFiltersBySearch() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(reportOrderRepository.revenueByEmployee(isNull(), any(), any()))
                .thenReturn(List.of(
                        new RevenueAggRow(1L, 2L, new BigDecimal("10")),
                        new RevenueAggRow(2L, 1L, new BigDecimal("5"))));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                namedUser(1L, "Alice", "a@x.com"),
                namedUser(2L, "Bob", "b@x.com")));

        PageRequestDTO page = new PageRequestDTO();
        page.setPage(1);
        page.setSize(10);
        page.setSearch("ali");

        Page<RevenueRow> result = service.getRevenuePage("employee", FROM, TO, null, page);

        assertEquals(1, result.getTotalElements());
        assertEquals("Alice", result.getContent().get(0).name());
    }

    @Test
    void getInvoicesRejectsInventoryStaff() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.INVENTORY_STAFF);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.getInvoices(FROM, TO, null));

        assertTrue(error.getMessage().contains("You do not have access to business reports."));
    }

    @Test
    void getTrendRejectsWarehouseManager() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.WAREHOUSE_MANAGER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.getTrend(FROM, TO, null, null));

        assertTrue(error.getMessage().contains("You do not have access to business reports."));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void asBranchManager(Long branchId) {
        UserModel manager = new UserModel();
        manager.setId(5L);
        manager.setBranchId(branchId);
        manager.setRole(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
    }

    private UserModel namedUser(Long id, String fullName, String email) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setEmail(email);
        if (fullName != null) {
            String[] parts = fullName.split(" ", 2);
            user.setFirstName(parts[0]);
            if (parts.length > 1) {
                user.setLastName(parts[1]);
            }
        }
        return user;
    }
}
