package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "branches")
public class BranchModel extends BaseModel {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(name = "operating_hours", nullable = false, length = 255)
    private String operatingHours;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    /** Khu vực giao hàng (dùng cho gom đơn vận chuyển). */
    @Column(name = "area", length = 100)
    private String area;

    /** Tuyến giao hàng (dùng cho gom đơn vận chuyển). */
    @Column(name = "route", length = 100)
    private String route;
}
