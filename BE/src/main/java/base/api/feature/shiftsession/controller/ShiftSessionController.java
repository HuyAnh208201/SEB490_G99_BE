package base.api.feature.shiftsession.controller;

import base.api.feature.shiftsession.dto.request.CloseInventoryShiftRequest;
import base.api.feature.shiftsession.dto.request.ConfirmHandoverRequest;
import base.api.feature.shiftsession.dto.request.ConfirmOpeningFundRequest;
import base.api.feature.shiftsession.dto.request.ConfirmVerificationRequest;
import base.api.feature.shiftsession.dto.request.ReviewSessionRequest;
import base.api.feature.shiftsession.dto.request.SaveClosingDraftRequest;
import base.api.feature.shiftsession.dto.request.StartShiftRequest;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.service.IShiftSessionService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shift-sessions")
@Tag(name = "Shift Sessions", description = "Cashier POS: open/close shift session (consumes BM-published shifts via /api/shifts)")
public class ShiftSessionController extends BaseAPIController {

    @Autowired
    private IShiftSessionService shiftSessionService;

    @Operation(summary = "Current shift session state for logged-in staff")
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/current")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> getCurrent() {
        return success(shiftSessionService.getCurrent());
    }

    @Operation(summary = "Opening context (assigned shift + opening fund)")
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/opening")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> getOpening() {
        return success(shiftSessionService.getOpeningContext());
    }

    @Operation(summary = "Confirm opening fund (cashier)")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/confirm-opening-fund")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> confirmOpeningFund(
            @RequestBody(required = false) ConfirmOpeningFundRequest request) {
        return success(
                shiftSessionService.confirmOpeningFund(request != null ? request : new ConfirmOpeningFundRequest()),
                "Opening fund confirmed.");
    }

    @Operation(summary = "Start shift (open session)")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/start")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> startShift(
            @RequestBody(required = false) StartShiftRequest request) {
        return success(
                shiftSessionService.startShift(request != null ? request : new StartShiftRequest()),
                "Shift started.");
    }

    @Operation(summary = "Closing context (summary, verification, handover)")
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/closing")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> getClosing() {
        return success(shiftSessionService.getClosingContext());
    }

    @Operation(summary = "Confirm high-value product verification")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/confirm-verification")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> confirmVerification(
            @Valid @RequestBody ConfirmVerificationRequest request) {
        return success(shiftSessionService.confirmVerification(request), "Verification saved.");
    }

    @Operation(summary = "Confirm cash handover")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/confirm-handover")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> confirmHandover(
            @Valid @RequestBody ConfirmHandoverRequest request) {
        return success(shiftSessionService.confirmHandover(request), "Handover confirmed.");
    }

    @Operation(summary = "Save shift closing draft")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/closing/draft")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> saveDraft(
            @RequestBody(required = false) SaveClosingDraftRequest request) {
        return success(
                shiftSessionService.saveClosingDraft(request != null ? request : new SaveClosingDraftRequest()),
                "Draft saved.");
    }

    @Operation(summary = "Close cashier shift")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/close")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> closeCashier() {
        return success(shiftSessionService.closeCashierShift(), "Shift closed.");
    }

    @Operation(summary = "Close inventory staff shift")
    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/close-inventory")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> closeInventory(
            @RequestBody(required = false) CloseInventoryShiftRequest request) {
        return success(
                shiftSessionService.closeInventoryShift(
                        request != null ? request : new CloseInventoryShiftRequest()),
                "Shift closed.");
    }

    @Operation(summary = "Shift session history")
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/history")
    public ResponseEntity<TFUResponse<List<ShiftSessionResponse>>> history() {
        return success(shiftSessionService.getHistory());
    }

    @Operation(summary = "Cashier shifts pending cash-discrepancy approval (branch manager)")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @GetMapping("/pending")
    public ResponseEntity<TFUResponse<List<ShiftSessionResponse>>> pendingApprovals() {
        return success(shiftSessionService.getPendingApprovals());
    }

    @Operation(summary = "Approve a cashier shift cash discrepancy (branch manager)")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewSessionRequest request) {
        String note = request != null ? request.getNote() : null;
        return success(shiftSessionService.approveSession(id, note), "Shift session approved.");
    }

    @Operation(summary = "Reject a cashier shift cash discrepancy — cashier recounts (branch manager)")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewSessionRequest request) {
        String note = request != null ? request.getNote() : null;
        return success(shiftSessionService.rejectSession(id, note), "Shift session rejected. Cashier must recount.");
    }
}
