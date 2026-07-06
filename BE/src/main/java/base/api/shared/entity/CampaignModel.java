package base.api.shared.entity;

import base.api.shared.converter.CampaignScopeConverter;
import base.api.shared.converter.CampaignStatusConverter;
import base.api.shared.converter.CampaignTypeConverter;
import base.api.shared.enums.CampaignScope;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "campaigns")
public class CampaignModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Convert(converter = CampaignTypeConverter.class)
    @Column(nullable = false, length = 50)
    private CampaignType type;

    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(columnDefinition = "json")
    private String conditions;

    @Convert(converter = CampaignScopeConverter.class)
    @Column(nullable = false, length = 50)
    private CampaignScope scope;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Convert(converter = CampaignStatusConverter.class)
    @Column(nullable = false, length = 50)
    private CampaignStatus status = CampaignStatus.DEACTIVATED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
