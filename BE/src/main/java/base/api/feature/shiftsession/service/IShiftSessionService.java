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

    List<ShiftSessionResponse> listBranchSessionsForManager();

    List<ShiftSessionResponse> listPendingReconciliation();

    ShiftSessionResponse getReconciliationDetail(Long sessionId);

    ShiftSessionResponse decideReconciliation(Long sessionId, ReconcileShiftSessionRequest request);
}
