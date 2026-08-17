package base.api.feature.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product code is required.")
    @Size(max = 255, message = "Product code must not exceed 255 characters.")
    private String code;

    @Size(max = 255, message = "Barcode must not exceed 255 characters.")
    private String barcode;

    @NotBlank(message = "Product name is required.")
    @Size(max = 255, message = "Product name must not exceed 255 characters.")
    private String name;

    @NotNull(message = "Category is required.")
    private Integer categoryId;

    @NotBlank(message = "Unit is required.")
    @Size(max = 255, message = "Unit must not exceed 255 characters.")
    private String unit;

    @Size(max = 64, message = "Import unit must not exceed 64 characters.")
    private String importUnit;

    private Integer unitsPerImportUnit;

    private Integer supplierId;

    @NotNull(message = "Reference import price is required.")
    private BigDecimal referenceImportPrice;

    @NotNull(message = "Default sale price is required.")
    private BigDecimal defaultSalePrice;

    private Boolean refundable = true;

    private String description;

    private String imageUrl;
}
