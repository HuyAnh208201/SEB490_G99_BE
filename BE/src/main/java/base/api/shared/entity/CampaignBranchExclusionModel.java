package base.api.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "campaign_branch_exclusions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "branch_id"})
)
public class CampaignBranchExclusionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;
}
