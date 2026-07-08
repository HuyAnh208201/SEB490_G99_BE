package base.api.feature.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private Integer id;
    private String code;
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private Integer categoryId;
    private String categoryName;
    private String unit;
    private BigDecimal referenceImportPrice;
    private BigDecimal defaultSalePrice;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
