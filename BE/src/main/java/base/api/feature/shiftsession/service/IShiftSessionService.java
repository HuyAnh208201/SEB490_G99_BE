package base.api.feature.shiftsession.service;

import base.api.feature.shiftsession.dto.request.ReconcileShiftSessionRequest;
import base.api.feature.shiftsession.dto.request.CloseInventoryShiftRequest;
import base.api.feature.shiftsession.dto.request.ConfirmHandoverRequest;
import base.api.feature.shiftsession.dto.request.ConfirmOpeningFundRequest;
import base.api.feature.shiftsession.dto.request.ConfirmVerificationRequest;
import base.api.feature.shiftsession.dto.request.SaveClosingDraftRequest;
import base.api.feature.shiftsession.dto.request.StartShiftRequest;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;

import java.util.List;

public interface IShiftSessionService {

    ShiftSessionResponse getCurrent();

    ShiftSessionResponse getOpeningContext();

    ShiftSessionResponse confirmOpeningFund(ConfirmOpeningFundRequest request);

    ShiftSessionResponse startShift(StartShiftRequest request);

    ShiftSessionResponse getClosingContext();

    ShiftSessionResponse confirmVerification(ConfirmVerificationRequest request);

    ShiftSessionResponse confirmHandover(ConfirmHandoverRequest request);

    ShiftSessionResponse saveClosingDraft(SaveClosingDraftRequest request);

    ShiftSessionResponse closeCashierShift();

    ShiftSessionResponse closeInventoryShift(CloseInventoryShiftRequest request);

    List<ShiftSessionResponse> getHistory();

    base.api.shared.dto.PageResponseDTO<ShiftSessionResponse> getHistoryPage(
            base.api.shared.dto.PageRequestDTO pageRequest);

    List<ShiftSessionResponse> listBranchSessionsForManager();

    List<ShiftSessionResponse> listPendingReconciliation();

    /**
     * BM reconciliation list.
     *
     * @param discrepancyFilter {@code with} (default) | {@code without} | {@code all}
     * @param statusFilter optional {@link base.api.shared.enums.ShiftSessionStatus} name; blank uses mode default
     */
    List<ShiftSessionResponse> listReconciliation(String discrepancyFilter, String statusFilter);

    ShiftSessionResponse getReconciliationDetail(Long sessionId);

    ShiftSessionResponse decideReconciliation(Long sessionId, ReconcileShiftSessionRequest request);

    /** Attendance row for BM branch audit (assignment + session timing). */
    List<base.api.feature.shiftsession.dto.response.BranchAttendanceResponse> listBranchAttendance(
            java.time.LocalDate from,
            java.time.LocalDate to);

    /** Completed refunds at BM branch for audit. */
    List<base.api.feature.shiftsession.dto.response.BranchRefundResponse> listBranchRefunds(
            java.time.LocalDate from,
            java.time.LocalDate to);

    /** Auto-close cashier sessions still open after shift end + grace period. */
    int autoCloseOverdueSessions();

    /** Delete closed sessions whose shift start is still in the future (demo clock skew). */
    int purgeFutureClosedSessions();
}

