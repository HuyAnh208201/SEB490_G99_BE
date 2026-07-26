package base.api.feature.shiftsession.service;

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

    /**
     * BM: các ca cashier đang chờ phê duyệt chênh lệch tiền (PENDING_APPROVAL)
     * thuộc chi nhánh của BM đang đăng nhập.
     */
    List<ShiftSessionResponse> getPendingApprovals();

    /**
     * BM phê duyệt chênh lệch tiền ca: PENDING_APPROVAL → APPROVED, lưu người/thời điểm
     * duyệt và ghi chú.
     */
    ShiftSessionResponse approveSession(Long id, String note);

    /**
     * BM từ chối: PENDING_APPROVAL → PENDING_HANDOVER (handoverConfirmed=false) để cashier
     * đếm lại rồi đóng ca lại. Lưu người/thời điểm duyệt và ghi chú lý do.
     */
    ShiftSessionResponse rejectSession(Long id, String note);
}
