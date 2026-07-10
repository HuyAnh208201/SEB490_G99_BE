package base.api.feature.purchaserequest.mapper;

import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestDetailResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class PurchaseRequestMapper {

    private static final DateTimeFormatter REQUEST_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public PurchaseRequestSummaryResponse toSummaryResponse(
            PurchaseRequestModel request,
            Integer itemCount,
            BranchModel branch,
            UserModel createdBy
    ) {
        PurchaseRequestSummaryResponse response = new PurchaseRequestSummaryResponse();
        response.setId(request.getId());
        response.setRequestNumber(toRequestNumber(request));
        response.setCreatedAt(request.getCreatedAt());
        response.setBranchId(request.getBranchId());
        response.setBranchName(branch == null ? null : branch.getName());
        response.setItemCount(itemCount);
        response.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        response.setCreatedBy(request.getCreatedBy());
        response.setCreatedByName(createdBy == null ? null : createdBy.getFullName());
        return response;
    }

    public PurchaseRequestResponse toResponse(
            PurchaseRequestModel request,
            BranchModel branch,
            UserModel createdBy,
            UserModel approvedBy,
            List<PurchaseRequestDetailModel> items,
            Map<Integer, ProductModel> productsById
    ) {
        return toResponse(request, branch, createdBy, approvedBy, items, productsById, Map.of());
    }

    public PurchaseRequestResponse toResponse(
            PurchaseRequestModel request,
            BranchModel branch,
            UserModel createdBy,
            UserModel approvedBy,
            List<PurchaseRequestDetailModel> items,
            Map<Integer, ProductModel> productsById,
            Map<Integer, Integer> warehouseStockByProduct
    ) {
        PurchaseRequestResponse response = new PurchaseRequestResponse();
        response.setId(request.getId());
        response.setRequestNumber(toRequestNumber(request));
        response.setBranchId(request.getBranchId());
        response.setBranchName(branch == null ? null : branch.getName());
        response.setCreatedBy(request.getCreatedBy());
        response.setCreatedByName(createdBy == null ? null : createdBy.getFullName());
        response.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        response.setApprovedBy(request.getApprovedBy());
        response.setApprovedByName(approvedBy == null ? null : approvedBy.getFullName());
        response.setApprovedAt(request.getApprovedAt());
        response.setRejectReason(request.getRejectReason());
        response.setRequestDate(toRequestDate(request));
        response.setCreatedAt(request.getCreatedAt());
        response.setNotes(request.getReason());
        Map<Integer, Integer> warehouseStock = warehouseStockByProduct == null ? Map.of() : warehouseStockByProduct;
        response.setItems(items.stream()
                .map(item -> toDetailResponse(
                        item,
                        productsById.get(item.getProductId()),
                        warehouseStock.get(item.getProductId())))
                .toList());
        return response;
    }

    public PurchaseRequestDetailResponse toDetailResponse(PurchaseRequestDetailModel detail, ProductModel product) {
        return toDetailResponse(detail, product, null);
    }

    public PurchaseRequestDetailResponse toDetailResponse(
            PurchaseRequestDetailModel detail,
            ProductModel product,
            Integer warehouseStock
    ) {
        PurchaseRequestDetailResponse response = new PurchaseRequestDetailResponse();
        response.setId(detail.getId());
        response.setProductId(detail.getProductId());
        response.setProductCode(product == null ? null : product.getCode());
        response.setProductName(product == null ? null : product.getName());
        response.setCategoryName(product == null || product.getCategory() == null ? null : product.getCategory().getName());
        response.setUnit(product == null ? null : product.getUnit());
        response.setRequestedQty(detail.getRequestedQty());
        response.setApprovedQuantity(detail.getApprovedQuantity());
        response.setSupplierId(detail.getSupplierId());
        response.setWarehouseStock(warehouseStock);
        return response;
    }

    public RecommendedProductResponse toRecommendedProductResponse(
            ProductModel product,
            Integer currentStock,
            Integer reorderPoint,
            Integer suggestedQty
    ) {
        RecommendedProductResponse response = new RecommendedProductResponse();
        response.setProductId(product.getId());
        response.setProductCode(product.getCode());
        response.setProductName(product.getName());
        response.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
        response.setUnit(product.getUnit());
        response.setCurrentStock(currentStock);
        response.setReorderPoint(reorderPoint);
        response.setSuggestedQty(suggestedQty);
        return response;
    }

    public ProductSearchResponse toProductSearchResponse(ProductModel product) {
        ProductSearchResponse response = new ProductSearchResponse();
        response.setProductId(product.getId());
        response.setProductCode(product.getCode());
        response.setBarcode(product.getBarcode());
        response.setProductName(product.getName());
        response.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
        response.setUnit(product.getUnit());
        return response;
    }

    public PurchaseRequestDetailModel buildDetailSnapshot(Long requestId, ProductModel product, Integer requestedQty) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(requestId);
        detail.setProductId(product.getId());
        detail.setRequestedQty(requestedQty);
        return detail;
    }

    private String toRequestNumber(PurchaseRequestModel request) {
        if (request.getId() == null) {
            return null;
        }
        LocalDate date = toRequestDate(request);
        return "REQ-" + date.format(REQUEST_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", request.getId());
    }

    private LocalDate toRequestDate(PurchaseRequestModel request) {
        if (request.getCreatedAt() == null) {
            return LocalDate.now();
        }
        return request.getCreatedAt().toLocalDate();
    }
}
