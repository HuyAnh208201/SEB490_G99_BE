package base.api.feature.product.mapper;

import base.api.feature.product.dto.response.ProductResponse;
import base.api.shared.entity.ProductModel;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(ProductModel product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCode(product.getCode());
        response.setBarcode(product.getBarcode());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setImageUrl(product.getImageUrl());
        response.setUnit(product.getUnit());
        response.setImportUnit(product.getImportUnit());
        response.setUnitsPerImportUnit(product.getUnitsPerImportUnit());
        response.setSupplierId(product.getSupplierId());
        response.setScope(product.getScope());
        response.setBranchId(product.getBranchId());
        response.setReferenceImportPrice(product.getReferenceImportPrice());
        response.setDefaultSalePrice(product.getDefaultSalePrice());
        response.setRefundable(!Boolean.FALSE.equals(product.getRefundable()));
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(product.getCategory().getName());
        }

        return response;
    }

    public ProductResponse toListResponse(ProductModel product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCode(product.getCode());
        response.setBarcode(product.getBarcode());
        response.setName(product.getName());
        response.setImageUrl(product.getImageUrl());
        response.setUnit(product.getUnit());
        response.setImportUnit(product.getImportUnit());
        response.setUnitsPerImportUnit(product.getUnitsPerImportUnit());
        response.setSupplierId(product.getSupplierId());
        response.setScope(product.getScope());
        response.setBranchId(product.getBranchId());
        response.setReferenceImportPrice(product.getReferenceImportPrice());
        response.setDefaultSalePrice(product.getDefaultSalePrice());
        response.setRefundable(!Boolean.FALSE.equals(product.getRefundable()));
        response.setStatus(product.getStatus());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(product.getCategory().getName());
        }

        return response;
    }
}
