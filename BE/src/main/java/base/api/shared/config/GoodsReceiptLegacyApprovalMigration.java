package base.api.shared.config;

import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.shared.entity.GoodsReceiptModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.enums.PurchaseRequestStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fixes legacy receipts left PENDING_APPROVAL after stock was already applied and PR marked RECEIVED.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class GoodsReceiptLegacyApprovalMigration {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptLegacyApprovalMigration.class);
    private static final String STATUS_PENDING = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";

    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @PostConstruct
    public void migrate() {
        try {
            List<GoodsReceiptModel> pending = goodsReceiptRepository.findAll().stream()
                    .filter(receipt -> STATUS_PENDING.equalsIgnoreCase(normalize(receipt.getStatus())))
                    .toList();
            if (pending.isEmpty()) {
                return;
            }

            List<Long> requestIds = pending.stream()
                    .map(GoodsReceiptModel::getPurchaseRequestId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            if (requestIds.isEmpty()) {
                return;
            }

            Map<Long, PurchaseRequestStatus> statusByRequestId = purchaseRequestRepository.findAllById(requestIds).stream()
                    .collect(Collectors.toMap(PurchaseRequestModel::getId, PurchaseRequestModel::getStatus, (a, b) -> a));

            List<GoodsReceiptModel> toApprove = new ArrayList<>();
            for (GoodsReceiptModel receipt : pending) {
                PurchaseRequestStatus prStatus = statusByRequestId.get(receipt.getPurchaseRequestId());
                if (prStatus == PurchaseRequestStatus.RECEIVED) {
                    receipt.setStatus(STATUS_APPROVED);
                    toApprove.add(receipt);
                }
            }
            if (!toApprove.isEmpty()) {
                goodsReceiptRepository.saveAll(toApprove);
                log.info("Auto-approved {} legacy goods receipt(s) already tied to RECEIVED purchase requests", toApprove.size());
            }
        } catch (Exception ex) {
            log.warn("Goods receipt legacy approval migration skipped: {}", ex.getMessage());
        }
    }

    private String normalize(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING;
        }
        return status.trim().toUpperCase().replace(" ", "_");
    }
}
