package base.api.feature.auth.controller;

import base.api.feature.auth.dto.request.*;
import base.api.feature.auth.dto.response.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.feature.auth.dto.response.UserDto;
import base.api.shared.entity.UserModel;
import base.api.feature.auth.service.IAuthService;
import base.api.feature.auth.service.IUserService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Xác thực (Auth)", description = "Đăng nhập, đăng ký, quên mật khẩu, xác thực email và quản lý tài khoản")
public class AuthController extends BaseAPIController {

    @Autowired
    private IAuthService authService;

    @Autowired
    private IUserService userService;

    @Autowired
    private ModelMapper mapper;

    @Value("${url.client-url:http://localhost:3000}")
    private String clientBaseUrl;

    @Operation(summary = "Đăng nhập", description = "**Public.** Xác thực tên đăng nhập và mật khẩu, trả về JWT token. Cần xác thực email trước khi đăng nhập.")
    @SecurityRequirements
    @PostMapping("login")
    public ResponseEntity<TFUResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest dto) {
        try {
            return success(authService.login(dto));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @Operation(summary = "Đăng xuất", description = "**Cần đăng nhập.** Thu hồi JWT token hiện tại để không thể dùng lại.")
    @PostMapping("logout")
    public ResponseEntity<TFUResponse<String>> logout() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized("Chưa đăng nhập");
        }

        try {
            authService.logout(authHeader.substring(7));
            return success("Đăng xuất thành công");
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @Operation(summary = "Đăng ký", description = "**Public.** Tạo tài khoản mới. Sau khi đăng ký cần xác thực email qua link gửi đến hộp thư.")
    @PostMapping("register")
    public ResponseEntity<TFUResponse<UserModel>> register(@Valid @RequestBody RegisterDto dto){
       try{
           UserModel user = userService.registerUser(dto);
           if(user == null ){
               return badRequest("Không tạo được user");
           }
           return success(user);
       }
         catch (Exception e){
              return badRequest(e.getMessage());
         }
    }


    @Operation(summary = "Lấy user theo ID", description = "**USER_MANAGEMENT_LIST** — Admin, Director, Branch Manager.")
    @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")
    @GetMapping("get-user-by-id")
    public ResponseEntity<TFUResponse<UserModel>> getUserById(
            @Parameter(description = "ID của user") @RequestParam Long id){
        UserModel user = userService.findById(id);
        if(user == null){
            return badRequest("Không tìm thấy user");
        }
        return success(user);
    }

    @Operation(summary = "Thông tin tài khoản hiện tại", description = "**Cần đăng nhập.** Lấy thông tin user đang đăng nhập từ JWT token.")
    @GetMapping("me")
    public ResponseEntity<TFUResponse<UserDto>> getUserInfo(){
        UserModel user = userService.findById(getCurrentUserId());
        if(user == null){
            return badRequest("Không tìm thấy user");
        }

        UserDto userDto = mapper.map(user, UserDto.class);

        return success(userDto);
    }

    @Operation(summary = "Danh sách tất cả user", description = "**USER_MANAGEMENT_LIST** — Admin, Director, Branch Manager.")
    @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")
    @GetMapping("get-list-users")
    public ResponseEntity<TFUResponse<Iterable<UserModel>>> getListUsers(){
        Iterable<UserModel> users = userService.getAllUsers();
        return success(users);
    }

    @Operation(summary = "Bắt đầu quên mật khẩu", description = "**Public.** Gửi mã xác thực đến email/SĐT. Dùng mã này để hoàn tất đặt lại mật khẩu.")
    @PostMapping("forgot-password/initiate")
    public ResponseEntity<TFUResponse<InitiateForgotPasswordResponse>> initiateForgotPassword(
            @Valid @RequestBody InitiateForgotPasswordDto dto) {
        try {
            InitiateForgotPasswordResponse response = userService.initiateForgotPassword(dto.getContactInfo());
            return success(response);
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Hoàn tất đặt lại mật khẩu", description = "**Public.** Dùng mã OTP và mật khẩu mới để đổi mật khẩu.")
    @PostMapping("forgot-password/complete")
    public ResponseEntity<TFUResponse<String>> completeForgotPassword(
            @Valid @RequestBody CompleteForgotPasswordDto dto) {
        try {
            userService.completeForgotPassword(dto);
            return success("Đặt lại mật khẩu thành công");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Xác thực email", description = "**Public.** Xác thực tài khoản qua link trong email sau khi đăng ký. Thành công: redirect `/email/verify-success`; lỗi: `/email/verify-failed`.")
    @GetMapping("verify-email")
    public ResponseEntity<Void> verifyEmail(
            @Parameter(description = "Token xác thực từ email") @RequestParam String token) {
        try {
            userService.verifyEmailByToken(token);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", clientBaseUrl + "/email/verify-success")
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", clientBaseUrl + "/email/verify-failed")
                    .build();
        }
    }

    @Operation(summary = "Gửi lại email xác thực", description = "**Public.** Dùng khi user chưa verify email và muốn nhận lại link xác thực. Nhận email hoặc tên đăng nhập.")
    @PostMapping("resend-verification")
    public ResponseEntity<TFUResponse<String>> resendVerification(
            @Valid @RequestBody InitiateForgotPasswordDto dto) {
        try {
            userService.resendVerificationEmail(dto.getContactInfo());
            return success("Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Cập nhật hồ sơ", description = "**Cần đăng nhập.** Cập nhật thông tin cá nhân (tên, email, SĐT,...) của user hiện tại.")
    @PostMapping("update-profile")
    public ResponseEntity<TFUResponse<UserModel>> updateProfile(@Valid @RequestBody UpdateProfileDto dto) {
        try {
            UserModel updatedUser = userService.updateProfile(getCurrentUserId(), dto);
            return success(updatedUser);
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Tạo tài khoản", description = "**USER_DETAILS_EDIT** — Admin, Director, Branch Manager (theo quy tắc gán role).")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @PostMapping("admin/create-user")
    public ResponseEntity<TFUResponse<UserModel>> createUserByAdmin(@Valid @RequestBody CreateUserByAdminDto dto) {
        try {
            UserModel creator = userService.findById(getCurrentUserId());
            if (creator == null) {
                return unauthorized("Chưa đăng nhập");
            }
            if (!creator.getRole().canManageUsers()) {
                return forbidden("Không có quyền quản lý user");
            }
            if (!creator.getRole().canAssignRole(dto.getRole())) {
                return forbidden("Không được phép gán role này");
            }
            UserModel user = userService.createUserByAdmin(dto);
            return success(user, "Tạo tài khoản thành công. Mật khẩu tạm đã được gửi qua email.");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Đổi mật khẩu", description = "**Cần đăng nhập.** Đổi mật khẩu khi đã biết mật khẩu cũ.")
    @PostMapping("change-password")
    public ResponseEntity<TFUResponse<String>> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
        try {
            userService.changePassword(getCurrentUserId(), dto);
            return success("Đổi mật khẩu thành công");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }
}
