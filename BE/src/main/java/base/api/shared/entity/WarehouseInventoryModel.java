package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Tồn kho KHO TỔNG (central warehouse). Khác với branch_inventory (tồn kho chi nhánh).
 * Dùng để kho tổng quyết định "còn hàng / hết hàng" khi duyệt yêu cầu nhập hàng của chi nhánh.
 */
@Getter
@Setter
@Entity
@Table(name = "warehouse_inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uq_warehouse_inventory_product", columnNames = {"product_id"})
})
public class WarehouseInventoryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "quantity")
    private Integer quantity = 0;

    @Column(name = "reorder_point")
    private Integer reorderPoint = 0;
}
