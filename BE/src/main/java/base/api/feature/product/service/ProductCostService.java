package base.api.feature.product.service;

import base.api.feature.purchaseorder.repository.PurchaseOrderItemRepository;
import base.api.shared.entity.ProductModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resolves per-base-unit import cost from the latest WM supplier receipt,
 * falling back to catalog {@code referenceImportPrice}.
 */
@Service
public class ProductCostService {

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private ProductPackagingService productPackagingService;

    public BigDecimal unitCostForProduct(ProductModel product) {
        if (product == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal lastPackPrice = purchaseOrderItemRepository.findLatestReceivedUnitPrice(product.getId());
        if (lastPackPrice != null) {
            return baseUnitCostFromPackPrice(lastPackPrice, product);
        }
        return product.getReferenceImportPrice() != null ? product.getReferenceImportPrice() : BigDecimal.ZERO;
    }

    public BigDecimal baseUnitCostFromPackPrice(BigDecimal packPrice, ProductModel product) {
        if (packPrice == null) {
            return BigDecimal.ZERO;
        }
        int conversion = productPackagingService.topConversionQty(product);
        if (conversion < 1) {
            conversion = 1;
        }
        return packPrice.divide(BigDecimal.valueOf(conversion), 2, RoundingMode.HALF_UP);
    }
}
