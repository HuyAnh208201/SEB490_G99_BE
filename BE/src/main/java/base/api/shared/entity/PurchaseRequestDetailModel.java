package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_request_items")
public class PurchaseRequestDetailModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_request_id", nullable = false)
    private Long purchaseRequestId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQty;

    @Column(name = "approved_quantity")
    private Integer approvedQuantity;
}
