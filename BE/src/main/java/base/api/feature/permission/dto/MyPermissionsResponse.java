package base.api.feature.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MyPermissionsResponse {
    private String role;
    private List<WebPermissionDto> permissions;
}
