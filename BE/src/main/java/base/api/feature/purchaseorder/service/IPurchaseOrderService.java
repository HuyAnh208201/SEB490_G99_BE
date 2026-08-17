package base.api.feature.purchaseorder.service;

import base.api.feature.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import base.api.feature.purchaseorder.dto.response.PurchaseOrderResponse;
import base.api.feature.purchaseorder.dto.response.PurchaseProductOptionResponse;
import base.api.feature.purchaseorder.dto.response.RecommendedPurchaseProductResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IPurchaseOrderService {

    List<RecommendedPurchaseProductResponse> getRecommendedProducts();

    List<PurchaseProductOptionResponse> searchProducts(Integer supplierId, String keyword);

    PurchaseOrderResponse createOrder(CreatePurchaseOrderRequest request);

    List<PurchaseOrderResponse> getOrders();

    Page<PurchaseOrderResponse> getOrderPage(PageRequestDTO pageRequest, PurchaseOrderStatus status);

    PurchaseOrderResponse getOrder(Long id);

    PurchaseOrderResponse receiveOrder(Long id);

    PurchaseOrderResponse cancelOrder(Long id);
}
