package base.api.feature.shift.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.dto.request.CloseShiftRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.UpdateOpeningCashRequest;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.mapper.ShiftMapper;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftOpeningCashTest {

    private static final BigDecimal DEFAULT_FLOAT = new BigDecimal("2000000");
    private static final Long BRANCH_ID = 10L;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository assignmentRepository;

    @Mock
    private base.api.feature.posorder.repository.PaymentRepository paymentRepository;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private ShiftMapper shiftMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ShiftServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultOpeningCash", DEFAULT_FLOAT);
    }

    @Test
    void firstShiftOfDayFallsBackToConfiguredFloat() {
        signedInAs(UserRole.BRANCH_MANAGER);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(shiftRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        // No earlier shift that day → this one opens the day.
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of());
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());

        CreateShiftRequest request = new CreateShiftRequest();
        request.setBranchId(BRANCH_ID);
        request.setStartTime(LocalDateTime.of(2026, 7, 27, 8, 0));
        request.setEndTime(LocalDateTime.of(2026, 7, 27, 12, 0));

        service.create(request);

        ArgumentCaptor<ShiftModel> saved = ArgumentCaptor.forClass(ShiftModel.class);
        org.mockito.Mockito.verify(shiftRepository).save(saved.capture());
        assertEquals(0, DEFAULT_FLOAT.compareTo(saved.getValue().getOpeningCash()));
    }

    @Test
    void closingShiftHandsCountedCashToNextShiftOfTheDay() {
        ShiftModel closing = shift(1L, ShiftStatus.PUBLISHED, 8, 12);
        ShiftModel next = shift(2L, ShiftStatus.PUBLISHED, 12, 16);
        next.setOpeningCash(BigDecimal.ZERO);

        stubClose(closing, next);
        service.closeShift(1L, closeWith("3500000"));

        assertEquals(0, new BigDecimal("3500000").compareTo(next.getOpeningCash()));
    }

    @Test
    void closingShiftLeavesAnAlreadyClosedNextShiftUntouched() {
        ShiftModel closing = shift(1L, ShiftStatus.PUBLISHED, 8, 12);
        ShiftModel next = shift(2L, ShiftStatus.CLOSED, 12, 16);
        next.setOpeningCash(new BigDecimal("999000"));

        stubClose(closing, next);
        service.closeShift(1L, closeWith("3500000"));

        assertEquals(0, new BigDecimal("999000").compareTo(next.getOpeningCash()));
    }

    @Test
    void expectedCashIsOpeningFloatPlusCashTakenDuringTheShift() {
        ShiftModel closing = shift(1L, ShiftStatus.PUBLISHED, 8, 12);
        closing.setOpeningCash(new BigDecimal("2000000"));
        ShiftModel next = shift(2L, ShiftStatus.PUBLISHED, 12, 16);

        stubClose(closing, next);
        // Bán được 5 triệu tiền mặt trong ca.
        when(paymentRepository.sumCashTakenInShift(1L)).thenReturn(new BigDecimal("5000000"));

        // Đếm đúng 7 triệu = 2 quỹ + 5 bán → không được lệch đồng nào.
        service.closeShift(1L, closeWith("7000000"));

        assertEquals(0, new BigDecimal("7000000").compareTo(closing.getExpectedCash()));
        assertEquals(0, BigDecimal.ZERO.compareTo(closing.getDifference()));
    }

    @Test
    void openingCashCannotBeEditedOnceTheShiftIsClosed() {
        ShiftModel closed = shift(1L, ShiftStatus.CLOSED, 8, 12);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(closed));
        signedInAs(UserRole.BRANCH_MANAGER);

        UpdateOpeningCashRequest request = new UpdateOpeningCashRequest();
        request.setOpeningCash(new BigDecimal("2500000"));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.updateOpeningCash(1L, request));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("DRAFT or PUBLISHED"));
    }

    // ---------------------------------------------------------------------

    private void stubClose(ShiftModel closing, ShiftModel next) {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(1L)).thenReturn(BigDecimal.ZERO);
        signedInAs(UserRole.CASHIER);
        when(shiftRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), eq(closing.getEndTime()), any()))
                .thenReturn(List.of(next));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());
    }

    private void signedInAs(UserRole role) {
        UserModel user = new UserModel();
        user.setId(5L);
        user.setBranchId(BRANCH_ID);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(role);
    }

    private ShiftModel shift(Long id, ShiftStatus status, int startHour, int endHour) {
        ShiftModel shift = new ShiftModel();
        shift.setId(id);
        shift.setBranchId(BRANCH_ID);
        shift.setStartTime(LocalDateTime.of(2026, 7, 27, startHour, 0));
        shift.setEndTime(LocalDateTime.of(2026, 7, 27, endHour, 0));
        shift.setExpectedCash(BigDecimal.ZERO);
        shift.setStatus(status);
        return shift;
    }

    private CloseShiftRequest closeWith(String actualCash) {
        CloseShiftRequest request = new CloseShiftRequest();
        request.setActualCash(new BigDecimal(actualCash));
        return request;
    }
}
