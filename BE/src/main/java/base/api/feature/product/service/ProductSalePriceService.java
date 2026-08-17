package base.api.feature.product.service;

import base.api.feature.product.dto.request.ScheduleProductSalePriceRequest;
import base.api.feature.product.dto.response.ProductSalePriceResponse;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.repository.ProductSalePriceRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductSalePriceModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProductSalePriceService {
    @Autowired private ProductSalePriceRepository priceRepository;
    @Autowired private IProductRepository productRepository;
    @Autowired private CurrentUserProvider currentUserProvider;

    @Transactional
    public ProductSalePriceResponse schedule(Integer productId, ScheduleProductSalePriceRequest request) {
        ProductModel product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found."));
        if (request.getEffectiveDate() == null || !request.getEffectiveDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Effective date must be after today.");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Retail price must be greater than or equal to 0.");
        }
        if (product.getReferenceImportPrice() != null
                && request.getPrice().compareTo(product.getReferenceImportPrice()) < 0) {
            throw new BadRequestException("Retail price cannot be lower than import price.");
        }
        if (priceRepository.existsByProductIdAndEffectiveDate(productId, request.getEffectiveDate())) {
            throw new ConflictException("A retail price is already scheduled for this date.");
        }
        ProductSalePriceModel model = new ProductSalePriceModel();
        model.setProductId(productId);
        model.setPrice(request.getPrice());
        model.setEffectiveDate(request.getEffectiveDate());
        model.setCreatedBy(currentUserProvider.getCurrentUserOrThrow().getId());
        return toResponse(priceRepository.save(model));
    }

    public BigDecimal effectivePrice(ProductModel product) {
        if (product == null || product.getId() == null) return null;
        return priceRepository
                .findFirstByProductIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        product.getId(), LocalDate.now())
                .map(ProductSalePriceModel::getPrice)
                .orElse(product.getDefaultSalePrice());
    }

    public Map<Integer, BigDecimal> effectivePrices(List<ProductModel> products) {
        Map<Integer, BigDecimal> prices = new LinkedHashMap<>();
        for (ProductModel product : products) {
            if (product != null && product.getId() != null) {
                prices.put(product.getId(), product.getDefaultSalePrice());
            }
        }
        if (prices.isEmpty()) {
            return prices;
        }
        for (ProductSalePriceModel scheduled :
                priceRepository.findEffectivePrices(List.copyOf(prices.keySet()), LocalDate.now())) {
            prices.put(scheduled.getProductId(), scheduled.getPrice());
        }
        return prices;
    }

    public List<ProductSalePriceResponse> history(Integer productId) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found.");
        }
        return priceRepository.findByProductIdOrderByEffectiveDateDesc(productId).stream()
                .map(this::toResponse).toList();
    }

    public ProductSalePriceModel nextPrice(Integer productId) {
        return priceRepository
                .findFirstByProductIdAndEffectiveDateGreaterThanOrderByEffectiveDateAsc(productId, LocalDate.now())
                .orElse(null);
    }

    private ProductSalePriceResponse toResponse(ProductSalePriceModel model) {
        ProductSalePriceResponse response = new ProductSalePriceResponse();
        response.setId(model.getId());
        response.setProductId(model.getProductId());
        response.setPrice(model.getPrice());
        response.setEffectiveDate(model.getEffectiveDate());
        response.setCreatedBy(model.getCreatedBy());
        response.setCreatedAt(model.getCreatedAt());
        return response;
    }
}
