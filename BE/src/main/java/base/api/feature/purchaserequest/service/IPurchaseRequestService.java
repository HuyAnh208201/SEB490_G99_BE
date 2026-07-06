package base.api.feature.purchaserequest.service;

import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IPurchaseRequestService {
    PurchaseRequestResponse createDraft(CreatePurchaseRequestRequest request);

    PurchaseRequestResponse saveDraft(Long id, SaveDraftRequest request);

    PurchaseRequestResponse submitRequest(Long id, SubmitPurchaseRequestRequest request);

    PurchaseRequestResponse cancelRequest(Long id);

    PurchaseRequestResponse getRequest(Long id);

    Page<PurchaseRequestSummaryResponse> getRequestHistory(PageRequestDTO pageRequest);

    List<RecommendedProductResponse> getRecommendedProducts();

    Page<ProductSearchResponse> searchProducts(String keyword, PageRequestDTO pageRequest);
}
