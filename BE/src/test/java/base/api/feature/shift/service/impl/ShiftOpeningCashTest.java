package base.api.feature.shift.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
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
}
