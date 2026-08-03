package base.api.feature.purchaserequest.service;

import base.api.feature.purchaserequest.dto.request.ApprovePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.ReceiveGoodsRequest;
import base.api.feature.purchaserequest.dto.request.RejectPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.ConsolidatedBranchResponse;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestBranchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IPurchaseRequestService {
    PurchaseRequestResponse createDraft(CreatePurchaseRequestRequest request);

    PurchaseRequestResponse saveDraft(Long id, SaveDraftRequest request);

    PurchaseRequestResponse submitRequest(Long id, SubmitPurchaseRequestRequest request);

    PurchaseRequestResponse approveRequest(Long id, ApprovePurchaseRequestRequest request);

    PurchaseRequestResponse rejectRequest(Long id, RejectPurchaseRequestRequest request);

    PurchaseRequestResponse receiveGoods(Long id, ReceiveGoodsRequest request);

    PurchaseRequestResponse cancelRequest(Long id);

    PurchaseRequestResponse getRequest(Long id);

    Page<PurchaseRequestSummaryResponse> getRequestHistory(PageRequestDTO pageRequest);

    Page<PurchaseRequestSummaryResponse> getRequestHistory(
            PageRequestDTO pageRequest,
            PurchaseRequestStatus status,
            Long branchId);

    List<PurchaseRequestBranchResponse> getWarehouseFilterBranches();

    List<RecommendedProductResponse> getRecommendedProducts();

    Page<ProductSearchResponse> searchProducts(
            String keyword,
            PageRequestDTO pageRequest,
            Integer categoryId,
            Boolean lowStockOnly,
            String stockSort);

    List<ConsolidatedBranchResponse> getConsolidatedRequests();

    Page<ConsolidatedBranchResponse> getConsolidatedRequestPage(PageRequestDTO pageRequest);
}
