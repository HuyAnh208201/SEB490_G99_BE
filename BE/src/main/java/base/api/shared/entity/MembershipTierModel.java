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
 * Shared loyalty membership tiers (same {@code membership_tiers} table used by the customer app).
 */
@Getter
@Setter
@Entity
@Table(name = "membership_tiers")
public class MembershipTierModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "min_points", nullable = false)
    private Long minPoints;

    @Column(name = "max_points")
    private Long maxPoints;

    @Column(name = "point_multiplier", nullable = false)
    private Double pointMultiplier = 1.0;

    @Column(name = "benefits_json", columnDefinition = "TEXT")
    private String benefitsJson;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;
}
