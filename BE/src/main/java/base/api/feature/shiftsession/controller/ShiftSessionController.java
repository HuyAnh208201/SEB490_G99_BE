package base.api.feature.shiftsession.controller;

import base.api.feature.shiftsession.dto.request.ReconcileShiftSessionRequest;
import base.api.feature.shiftsession.dto.request.CloseInventoryShiftRequest;
import base.api.feature.shiftsession.dto.request.ConfirmHandoverRequest;
import base.api.feature.shiftsession.dto.request.ConfirmOpeningFundRequest;
import base.api.feature.shiftsession.dto.request.ConfirmVerificationRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "Shift session history (past shifts only, paginated)")
    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/history")
    public ResponseEntity<TFUResponse<base.api.shared.dto.PageResponseDTO<ShiftSessionResponse>>> history(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        base.api.shared.dto.PageRequestDTO pageRequest = new base.api.shared.dto.PageRequestDTO();
        pageRequest.setPage(page);
        pageRequest.setSize(size);
        return success(shiftSessionService.getHistoryPage(pageRequest));
    }

    @Operation(summary = "Branch manager: active / pending shift sessions at branch")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("/branch/monitor")
    public ResponseEntity<TFUResponse<List<ShiftSessionResponse>>> branchMonitor() {
        return success(shiftSessionService.listBranchSessionsForManager());
    }

    @Operation(summary = "Branch manager: cash reconciliation queue")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @GetMapping("/reconciliation/pending")
    public ResponseEntity<TFUResponse<List<ShiftSessionResponse>>> pendingReconciliation() {
        return success(shiftSessionService.listPendingReconciliation());
    }

    @Operation(summary = "Branch manager: filtered cash reconciliation list")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @GetMapping("/reconciliation")
    public ResponseEntity<TFUResponse<List<ShiftSessionResponse>>> reconciliation(
            @RequestParam(required = false, defaultValue = "with") String discrepancy,
            @RequestParam(required = false) String status) {
        return success(shiftSessionService.listReconciliation(discrepancy, status));
    }

    @Operation(summary = "Branch manager: attendance audit (check-in vs shift start)")
    @PreAuthorize("@permissionChecker.hasAny('APPROVE_CASH_DISCREPANCY','SHIFT_MANAGEMENT','REPORTS_VIEW')")
    @GetMapping("/branch/attendance")
    public ResponseEntity<TFUResponse<List<base.api.feature.shiftsession.dto.response.BranchAttendanceResponse>>> branchAttendance(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return success(shiftSessionService.listBranchAttendance(from, to));
    }

    @Operation(summary = "Branch manager: completed refunds at branch")
    @PreAuthorize("@permissionChecker.hasAny('APPROVE_CASH_DISCREPANCY','SHIFT_MANAGEMENT','REPORTS_VIEW')")
    @GetMapping("/branch/refunds")
    public ResponseEntity<TFUResponse<List<base.api.feature.shiftsession.dto.response.BranchRefundResponse>>> branchRefunds(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return success(shiftSessionService.listBranchRefunds(from, to));
    }

    @Operation(summary = "Branch manager: reconciliation detail")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @GetMapping("/reconciliation/{sessionId}")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> reconciliationDetail(
            @PathVariable Long sessionId) {
        return success(shiftSessionService.getReconciliationDetail(sessionId));
    }

    @Operation(summary = "Branch manager: approve or reject cash difference")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PostMapping("/reconciliation/{sessionId}/decision")
    public ResponseEntity<TFUResponse<ShiftSessionResponse>> reconciliationDecision(
            @PathVariable Long sessionId,
            @Valid @RequestBody ReconcileShiftSessionRequest request) {
        return success(
                shiftSessionService.decideReconciliation(sessionId, request),
                Boolean.TRUE.equals(request.getApproved())
                        ? "Cash difference approved."
                        : "Cash difference rejected.");
    }
}
