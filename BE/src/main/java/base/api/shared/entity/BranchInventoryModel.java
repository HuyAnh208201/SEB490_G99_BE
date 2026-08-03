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

@Getter
@Setter
@Entity
@Table(name = "branch_inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uq_branch_inventory_branch_product", columnNames = {"branch_id", "product_id"})
})
public class BranchInventoryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "quantity")
    private Integer currentStock = 0;

    @Column(name = "reorder_point")
    private Integer reorderPoint = 0;
}
