package base.api.feature.purchaserequest.mapper;

import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestDetailResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class PurchaseRequestMapper {

    private static final DateTimeFormatter REQUEST_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ProductPackagingService productPackagingService;

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
        response.setSubmittedAt(request.getSubmittedAt());
        response.setDesiredReceiveDate(request.getDesiredReceiveDate());
        response.setSupplementalForReceiptId(request.getSupplementalForReceiptId());
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
        return toResponse(request, branch, createdBy, approvedBy, items, productsById, Map.of(), Map.of());
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
        return toResponse(
                request, branch, createdBy, approvedBy, items, productsById, warehouseStockByProduct, Map.of());
    }

    public PurchaseRequestResponse toResponse(
            PurchaseRequestModel request,
            BranchModel branch,
            UserModel createdBy,
            UserModel approvedBy,
            List<PurchaseRequestDetailModel> items,
            Map<Integer, ProductModel> productsById,
            Map<Integer, Integer> warehouseStockByProduct,
            Map<Integer, ProductPackagingModel> topPackagingsByProduct
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
        response.setSubmittedAt(request.getSubmittedAt());
        response.setDesiredReceiveDate(request.getDesiredReceiveDate());
        response.setSupplementalForReceiptId(request.getSupplementalForReceiptId());
        response.setNotes(request.getReason());
        Map<Integer, Integer> warehouseStock = warehouseStockByProduct == null ? Map.of() : warehouseStockByProduct;
        Map<Integer, ProductPackagingModel> topPackagings =
                topPackagingsByProduct == null ? Map.of() : topPackagingsByProduct;
        response.setItems(items.stream()
                .map(item -> toDetailResponse(
                        item,
                        productsById.get(item.getProductId()),
                        warehouseStock.get(item.getProductId()),
                        topPackagings.get(item.getProductId())))
                .toList());
        return response;
    }

    public PurchaseRequestDetailResponse toDetailResponse(PurchaseRequestDetailModel detail, ProductModel product) {
        return toDetailResponse(detail, product, null, null);
    }

    public PurchaseRequestDetailResponse toDetailResponse(
            PurchaseRequestDetailModel detail,
            ProductModel product,
            Integer warehouseStock
    ) {
        return toDetailResponse(detail, product, warehouseStock, null);
    }

    public PurchaseRequestDetailResponse toDetailResponse(
            PurchaseRequestDetailModel detail,
            ProductModel product,
            Integer warehouseStock,
            ProductPackagingModel topPackaging
    ) {
        PurchaseRequestDetailResponse response = new PurchaseRequestDetailResponse();
        response.setId(detail.getId());
        response.setProductId(detail.getProductId());
        response.setProductCode(product == null ? null : product.getCode());
        response.setProductName(product == null || product.getName() == null || product.getName().isBlank()
                ? (product == null ? null : product.getCode())
                : product.getName());
        response.setCategoryName(product == null || product.getCategory() == null ? null : product.getCategory().getName());
        response.setUnit(product == null ? null : product.getUnit());
        response.setRequestedQty(detail.getRequestedQty());
        response.setApprovedQuantity(detail.getApprovedQuantity());
        response.setSupplierId(detail.getSupplierId());
        if (product != null) {
            BigDecimal unitCost = product.getReferenceImportPrice();
            response.setUnitCost(unitCost);
            ProductPackagingModel top = topPackaging != null
                    ? topPackaging
                    : productPackagingService.getTopPackaging(product);
            int conversionQty = productPackagingService.conversionQtyOf(top);
            response.setTopPackagingLabel(top == null ? null : top.displayLabel());
            response.setTopPackagingConversionQty(conversionQty);
            Integer qty = detail.getRequestedQty();
            // unitCost is per base unit; requestedQty is TOP packs → multiply by conversion.
            if (unitCost != null && qty != null) {
                response.setLineCost(unitCost
                        .multiply(BigDecimal.valueOf(Math.max(1, conversionQty)))
                        .multiply(BigDecimal.valueOf(qty)));
            }
            boolean shortDate = product.getCategory() != null
                    && Boolean.TRUE.equals(product.getCategory().getShortDate());
            response.setShortDate(shortDate);
        } else {
            response.setShortDate(false);
        }
        response.setWarehouseStock(warehouseStock);
        return response;
    }

    public RecommendedProductResponse toRecommendedProductResponse(
            ProductModel product,
            Integer currentStock,
            Integer reorderPoint,
            Integer suggestedQty
    ) {
        return toRecommendedProductResponse(
                product, currentStock, reorderPoint, suggestedQty, productPackagingService.getTopPackaging(product));
    }

    public RecommendedProductResponse toRecommendedProductResponse(
            ProductModel product,
            Integer currentStock,
            Integer reorderPoint,
            Integer suggestedQty,
            ProductPackagingModel topPackaging
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
        ProductPackagingModel top = topPackaging != null
                ? topPackaging
                : productPackagingService.getTopPackaging(product);
        response.setTopPackagingLabel(top == null ? null : top.displayLabel());
        response.setTopPackagingConversionQty(productPackagingService.conversionQtyOf(top));
        BigDecimal unitCost = product.getReferenceImportPrice();
        response.setUnitCost(unitCost);
        response.setReferenceImportPrice(unitCost);
        return response;
    }

    public ProductSearchResponse toProductSearchResponse(ProductModel product) {
        return toProductSearchResponse(product, productPackagingService.getTopPackaging(product));
    }

    public ProductSearchResponse toProductSearchResponse(
            ProductModel product,
            ProductPackagingModel topPackaging
    ) {
        ProductSearchResponse response = new ProductSearchResponse();
        response.setProductId(product.getId());
        response.setProductCode(product.getCode());
        response.setBarcode(product.getBarcode());
        response.setProductName(product.getName());
        response.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
        response.setCategoryId(product.getCategory() == null ? null : product.getCategory().getId());
        response.setUnit(product.getUnit());
        BigDecimal unitCost = product.getReferenceImportPrice();
        response.setUnitCost(unitCost);
        response.setReferenceImportPrice(unitCost);
        ProductPackagingModel top = topPackaging != null
                ? topPackaging
                : productPackagingService.getTopPackaging(product);
        response.setTopPackagingLabel(top == null ? null : top.displayLabel());
        response.setTopPackagingConversionQty(productPackagingService.conversionQtyOf(top));
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
