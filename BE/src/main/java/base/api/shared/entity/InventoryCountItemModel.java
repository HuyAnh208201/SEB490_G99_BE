package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng sản phẩm trong phiên kiểm kê: số hệ thống, số đếm thực tế và chênh lệch.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_count_items")
public class InventoryCountItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty = 0;

    @Column(name = "counted_qty", nullable = false)
    private Integer countedQty = 0;

    @Column(name = "variance", nullable = false)
    private Integer variance = 0;

    @Column(name = "note", length = 500)
    private String note;
}
