package base.api.feature.branch.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BranchResponse {

    private Long id;
    private String name;
    private String address;
    private String phone;
    private String operatingHours;
    private Long managerId;
    private String managerName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
