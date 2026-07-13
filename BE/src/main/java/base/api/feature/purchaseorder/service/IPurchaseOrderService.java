package base.api.feature.purchaseorder.service;

import base.api.feature.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import base.api.feature.purchaseorder.dto.response.PurchaseOrderResponse;
import base.api.feature.purchaseorder.dto.response.PurchaseProductOptionResponse;
import base.api.feature.purchaseorder.dto.response.RecommendedPurchaseProductResponse;

import java.util.List;

public interface IPurchaseOrderService {

    List<RecommendedPurchaseProductResponse> getRecommendedProducts();

    List<PurchaseProductOptionResponse> searchProducts(String keyword);

    PurchaseOrderResponse createOrder(CreatePurchaseOrderRequest request);

    List<PurchaseOrderResponse> getOrders();

    PurchaseOrderResponse getOrder(Long id);

    PurchaseOrderResponse receiveOrder(Long id);

    PurchaseOrderResponse cancelOrder(Long id);
}
