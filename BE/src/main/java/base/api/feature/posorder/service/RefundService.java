package base.api.feature.posorder.service;

import base.api.feature.posorder.dto.response.RefundResponse;

import java.util.List;

/** POS refund operations, including review of records pending from the legacy workflow. */
public interface RefundService {

    /** Immediately refund a full order within the five-minute refund window. */
    RefundResponse requestRefund(Long orderId, String reason);

    /** Return refund records that were pending before immediate refunds were introduced. */
    List<RefundResponse> getPendingRefunds();

    /** Approve a legacy pending refund. */
    RefundResponse approveRefund(Long refundId, String note);

    /** Reject a legacy pending refund. */
    RefundResponse rejectRefund(Long refundId, String note);
}
