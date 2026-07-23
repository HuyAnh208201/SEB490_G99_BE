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
@Table(name = "shift_session_high_value_items")
public class ShiftSessionHighValueItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "expected_qty", nullable = false)
    private Integer expectedQty = 0;

    @Column(name = "actual_qty")
    private Integer actualQty;

    @Column(name = "difference")
    private Integer difference;
}
