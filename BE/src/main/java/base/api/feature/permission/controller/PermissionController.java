package base.api.feature.permission.controller;

import base.api.feature.auth.service.IUserService;
import base.api.feature.permission.dto.MyPermissionsResponse;
import base.api.feature.permission.dto.WebPermissionDto;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.security.WebPermission;
import base.api.shared.security.WebRolePermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Phân quyền (Permissions)", description = "Ma trận quyền Web System theo role")
public class PermissionController extends BaseAPIController {

    @Autowired
    private IUserService userService;

    @Operation(summary = "Quyền của user hiện tại", description = "Trả về role và danh sách màn hình/API được phép (theo ma trận Web System).")
    @GetMapping("me")
    public ResponseEntity<TFUResponse<MyPermissionsResponse>> myPermissions() {
        UserModel user = userService.findById(getCurrentUserId());
        if (user == null || user.getRole() == null) {
            return unauthorized("Chưa đăng nhập");
        }

        UserRole webRole = user.getRole().toWebRole();
        List<WebPermissionDto> permissions = WebRolePermissions.permissionsFor(webRole).stream()
                .map(p -> new WebPermissionDto(p.name(), p.getLabel()))
                .toList();

        MyPermissionsResponse data = new MyPermissionsResponse(webRole.name(), permissions);
        return success(data);
    }

    @Operation(summary = "Ma trận phân quyền đầy đủ", description = "Xem toàn bộ ma trận role × permission (4 role Web System).")
    @GetMapping("matrix")
    public ResponseEntity<TFUResponse<Map<String, List<String>>>> permissionMatrix() {
        Map<String, List<String>> matrix = WebRolePermissions.matrix().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> e.getValue().stream().map(WebPermission::name).sorted().toList()
                ));
        return success(matrix);
    }
}
