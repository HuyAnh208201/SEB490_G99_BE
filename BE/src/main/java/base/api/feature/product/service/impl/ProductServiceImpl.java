package base.api.feature.product.service.impl;

import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.mapper.ProductMapper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.IProductService;
import base.api.shared.entity.CategoryModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductServiceImpl implements IProductService {

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ICategoryRepository categoryRepository;

    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String normalizedCode = normalizeRequiredText(request.getCode(), "Product code is required.");
        String normalizedName = normalizeRequiredText(request.getName(), "Product name is required.");
        String normalizedUnit = normalizeRequiredText(request.getUnit(), "Unit is required.");
        String normalizedBarcode = normalizeNullableText(request.getBarcode());

        validateDuplicateCode(normalizedCode);
        validateDuplicateBarcode(normalizedBarcode, null);
        validatePrices(request.getReferenceImportPrice(), request.getDefaultSalePrice());

        CategoryModel category = resolveCategory(request.getCategoryId());

        ProductModel product = new ProductModel();
        product.setCode(normalizedCode);
        product.setBarcode(normalizedBarcode);
        product.setName(normalizedName);
        product.setCategory(category);
        product.setUnit(normalizedUnit);
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        product.setDefaultSalePrice(request.getDefaultSalePrice());
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus("active");

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Integer id, UpdateProductRequest request) {
        ProductModel product = findProductOrThrow(id);

        String normalizedName = normalizeRequiredText(request.getName(), "Product name is required.");
        String normalizedUnit = normalizeRequiredText(request.getUnit(), "Unit is required.");
        String normalizedBarcode = normalizeNullableText(request.getBarcode());
        String normalizedStatus = normalizeRequiredText(request.getStatus(), "Status is required.");

        validateDuplicateBarcode(normalizedBarcode, id);
        validatePrices(request.getReferenceImportPrice(), request.getDefaultSalePrice());

        product.setBarcode(normalizedBarcode);
        product.setName(normalizedName);
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setUnit(normalizedUnit);
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        product.setDefaultSalePrice(request.getDefaultSalePrice());
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus(normalizedStatus);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        ProductModel product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    @Override
    public ProductResponse getById(Integer id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }

    @Override
    public List<ProductResponse> getAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(productMapper::toListResponse)
                .toList();
    }

    private ProductModel findProductOrThrow(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private void validateDuplicateCode(String code) {
        if (productRepository.existsByCode(code)) {
            throw new ConflictException("Product code already exists.");
        }
    }

    private void validateDuplicateBarcode(String barcode, Integer currentId) {
        if (barcode == null) {
            return;
        }

        boolean exists = currentId == null
                ? productRepository.existsByBarcode(barcode)
                : productRepository.existsByBarcodeAndIdNot(barcode, currentId);

        if (exists) {
            throw new ConflictException("Barcode already exists.");
        }
    }

    private void validatePrices(BigDecimal referenceImportPrice, BigDecimal defaultSalePrice) {
        if (referenceImportPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Reference import price must be greater than or equal to 0.");
        }

        if (defaultSalePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Default sale price must be greater than or equal to 0.");
        }

        if (defaultSalePrice.compareTo(referenceImportPrice) < 0) {
            throw new BadRequestException("Default sale price must not be smaller than reference import price.");
        }
    }

    private CategoryModel resolveCategory(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found."));
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
