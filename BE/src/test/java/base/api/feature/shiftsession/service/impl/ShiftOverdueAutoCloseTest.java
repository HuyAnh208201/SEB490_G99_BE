package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.repository.ShiftSessionApprovalRepository;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftOverdueAutoCloseTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long EMPLOYEE_ID = 5L;
    private static final Long SHIFT_ID = 7L;
    private static final Long SESSION_ID = 1L;

    @Mock private ShiftSessionRepository sessionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ShiftSessionHighValueItemRepository highValueItemRepository;
    @Mock private ShiftSessionApprovalRepository approvalRepository;
    @Mock private ShiftAssignmentRepository assignmentRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IProductRepository productRepository;
    @Mock private InventoryCountSessionRepository inventoryCountSessionRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EmailService emailService;

    @InjectMocks
    private ShiftSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "autoCloseGraceMinutes", 30);
        ReflectionTestUtils.setField(service, "clientUrl", "http://localhost:5173");
    }

    @Test
    void autoCloseOverdueSessions_closesStaleOpenSessionAndNotifiesManager() {
        ShiftSessionModel session = overdueOpenSession();
        ShiftModel shift = overdueShift();
        UserModel cashier = cashierUser();
        UserModel manager = managerUser();
        BranchModel branch = new BranchModel();
        branch.setId(BRANCH_ID);
        branch.setName("Q1 Store");

        when(sessionRepository.findOverdueCashierSessions(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(session));
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.valueOf(500_000));
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(3L);
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());
        when(branchInventoryRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of());
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(cashier));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(userRepository.findActiveBranchManagers(BRANCH_ID)).thenReturn(List.of(manager));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        int closed = service.autoCloseOverdueSessions();

        assertEquals(1, closed);
        assertEquals(ShiftSessionStatus.COMPLETED, session.getStatus());
        verify(emailService).sendPlainTextEmail(eq("bm.q1@chainstore.vn"), anyString(), anyString());
    }

    @Test
    void autoCloseOverdueSessions_skipsWhenShiftStillWithinGracePeriod() {
        when(sessionRepository.findOverdueCashierSessions(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int closed = service.autoCloseOverdueSessions();

        assertEquals(0, closed);
        verify(sessionRepository, never()).save(any());
        verify(emailService, never()).sendPlainTextEmail(anyString(), anyString(), anyString());
    }

    private ShiftSessionModel overdueOpenSession() {
        ShiftSessionModel session = new ShiftSessionModel();
        session.setId(SESSION_ID);
        session.setShiftId(SHIFT_ID);
        session.setEmployeeId(EMPLOYEE_ID);
        session.setBranchId(BRANCH_ID);
        session.setRole(UserRole.CASHIER);
        session.setStatus(ShiftSessionStatus.OPEN);
        session.setOpeningFundAmount(BigDecimal.valueOf(2_000_000));
        session.setOpenedAt(LocalDateTime.now().minusHours(8));
        return session;
    }

    private ShiftModel overdueShift() {
        ShiftModel shift = new ShiftModel();
        shift.setId(SHIFT_ID);
        shift.setBranchId(BRANCH_ID);
        shift.setStartTime(LocalDateTime.now().minusHours(8));
        shift.setEndTime(LocalDateTime.now().minusHours(2));
        return shift;
    }

    private UserModel cashierUser() {
        UserModel user = new UserModel();
        user.setId(EMPLOYEE_ID);
        user.setFirstName("Cash");
        user.setLastName("ier");
        user.setEmail("cashier.q1@chainstore.vn");
        return user;
    }

    private UserModel managerUser() {
        UserModel user = new UserModel();
        user.setId(99L);
        user.setEmail("bm.q1@chainstore.vn");
        user.setFirstName("Branch");
        user.setLastName("Manager");
        return user;
    }
}
