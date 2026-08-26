package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Ghi lại mã giảm giá đã áp vào hoá đơn và số tiền nó giảm. */
@Getter
@Setter
@Entity
@Table(name = "order_discounts")
public class OrderDiscountModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Chụp lại mã lúc áp, phòng khi mã bị xoá sau này. */
    @Column(length = 64)
    private String code;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;
}
