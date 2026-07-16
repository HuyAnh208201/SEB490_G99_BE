package base.api.feature.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriticalRoleSlotsResponse {
    private boolean adminAvailable;
    private boolean directorAvailable;
    private boolean warehouseManagerAvailable;
}
