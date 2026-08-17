package base.api.feature.auth.controller;

import base.api.feature.auth.dto.request.*;
import base.api.feature.auth.dto.response.AuthResponse;
import base.api.feature.auth.dto.response.CriticalRoleSlotsResponse;
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
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication (Auth)", description = "Login, register, forgot password, email verification, and account management")
public class AuthController extends BaseAPIController {

    @Autowired
    private IAuthService authService;

    @Autowired
    private IUserService userService;

    @Autowired
    private ModelMapper mapper;

    @Value("${url.client-url:http://localhost:5175}")
    private String clientBaseUrl;

    @Operation(summary = "Login", description = "**Public.** Authenticate username and password, return a JWT token. Email verification is required before login.")
    @SecurityRequirements
    @PostMapping("login")
    public ResponseEntity<TFUResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest dto) {
        try {
            return success(authService.login(dto));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @Operation(summary = "Logout", description = "**Requires login.** Revoke the current JWT token so it cannot be reused.")
    @PostMapping("logout")
    public ResponseEntity<TFUResponse<String>> logout() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized("Not authenticated");
        }

        try {
            authService.logout(authHeader.substring(7));
            return success("Logged out successfully");
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @Operation(summary = "Register", description = "**Public.** Create a new account. After registration, verify email via the link sent to the inbox.")
    @PostMapping("register")
    public ResponseEntity<TFUResponse<UserModel>> register(@Valid @RequestBody RegisterDto dto){
       try{
           UserModel user = userService.registerUser(dto);
           if(user == null ){
               return badRequest("Unable to create user");
           }
           return success(user);
       }
         catch (Exception e){
              return badRequest(e.getMessage());
         }
    }


    @Operation(summary = "Get user by ID", description = "**USER_MANAGEMENT_LIST** — Admin, Director, Branch Manager.")
    @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")
    @GetMapping("get-user-by-id")
    public ResponseEntity<TFUResponse<UserModel>> getUserById(
            @Parameter(description = "User ID") @RequestParam Long id){
        UserModel user = userService.findById(id);
        if(user == null){
            return badRequest("User not found");
        }
        return success(user);
    }

    @Operation(summary = "Current account info", description = "**Requires login.** Get the currently authenticated user from the JWT token.")
    @GetMapping("me")
    public ResponseEntity<TFUResponse<UserDto>> getUserInfo(){
        UserModel user = userService.findById(getCurrentUserId());
        if(user == null){
            return badRequest("User not found");
        }

        UserDto userDto = toUserDto(user);

        return success(userDto);
    }

    @Operation(summary = "List all users", description = "**USER_MANAGEMENT_LIST** — Admin, Director, Branch Manager.")
    @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")
    @GetMapping("get-list-users")
    public ResponseEntity<TFUResponse<Iterable<UserModel>>> getListUsers(){
        Iterable<UserModel> users = userService.getAllUsers();
        return success(users);
    }

    @Operation(summary = "Search, filter and paginate users")
    @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")
    @GetMapping("get-list-users/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<UserDto>>> getUserPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status) {
        Page<UserDto> page = userService.getUserPage(pageRequest, role, branchId, status)
                .map(this::toUserDto);
        return successPage(page);
    }

    @Operation(summary = "Initiate forgot password", description = "**Public.** Send a verification code to email/phone. Use this code to complete password reset.")
    @PostMapping("forgot-password/initiate")
    public ResponseEntity<TFUResponse<InitiateForgotPasswordResponse>> initiateForgotPassword(
            @Valid @RequestBody InitiateForgotPasswordDto dto) {
        try {
            InitiateForgotPasswordResponse response =
                    userService.initiateForgotPassword(dto.getContactInfo(), dto.getFrontendBaseUrl());
            return success(response);
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Complete password reset", description = "**Public.** Use the OTP and new password to reset the password.")
    @PostMapping("forgot-password/complete")
    public ResponseEntity<TFUResponse<String>> completeForgotPassword(
            @Valid @RequestBody CompleteForgotPasswordDto dto) {
        try {
            userService.completeForgotPassword(dto);
            return success("Password reset successfully");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Verify email", description = "**Public.** Verify the account via the email link after registration. Success: redirect `/email/verify-success`; failure: `/email/verify-failed`.")
    @GetMapping("verify-email")
    public ResponseEntity<Void> verifyEmail(
            @Parameter(description = "Verification token from email") @RequestParam String token) {
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

    @Operation(summary = "Resend verification email", description = "**Public.** Used when the user has not verified email and wants another verification link. Accepts email or username.")
    @PostMapping("resend-verification")
    public ResponseEntity<TFUResponse<String>> resendVerification(
            @Valid @RequestBody InitiateForgotPasswordDto dto) {
        try {
            userService.resendVerificationEmail(dto.getContactInfo());
            return success("Verification email resent. Please check your inbox.");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Update profile", description = "**Requires login.** Update the current user profile (name, email, phone, etc.).")
    @PostMapping("update-profile")
    public ResponseEntity<TFUResponse<UserModel>> updateProfile(@Valid @RequestBody UpdateProfileDto dto) {
        try {
            UserModel updatedUser = userService.updateProfile(getCurrentUserId(), dto);
            return success(updatedUser);
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Create account", description = "**USER_DETAILS_EDIT** — Admin, Director, Branch Manager (according to role assignment rules).")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @PostMapping("admin/create-user")
    public ResponseEntity<TFUResponse<UserModel>> createUserByAdmin(@Valid @RequestBody CreateUserByAdminDto dto) {
        try {
            UserModel creator = userService.findById(getCurrentUserId());
            if (creator == null) {
                return unauthorized("Not authenticated");
            }
            if (!creator.getRole().canManageUsers()) {
                return forbidden("You do not have permission to manage users");
            }
            if (!creator.getRole().canAssignRole(dto.getRole())) {
                return forbidden("You are not allowed to assign this role");
            }
            UserModel user = userService.createUserByAdmin(dto, creator);
            return success(user, "Account created successfully. A temporary password has been sent by email.");
        } catch (BadRequestException | ConflictException | ForbiddenException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Change password", description = "**Requires login.** Change password when the current password is known.")
    @PostMapping("change-password")
    public ResponseEntity<TFUResponse<String>> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
        try {
            userService.changePassword(getCurrentUserId(), dto);
            return success("Password changed successfully");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "Update user status", description = "**USER_DETAILS_EDIT** — Admin/Director/BM by permission.")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @PatchMapping("admin/users/{id}/status")
    public ResponseEntity<TFUResponse<UserModel>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusDto dto) {
        UserModel actor = userService.findById(getCurrentUserId());
        if (actor == null) {
            return unauthorized("Not authenticated");
        }
        UserModel updated = userService.updateUserStatus(
                id,
                Boolean.TRUE.equals(dto.getActive()),
                actor,
                dto.getEmail(),
                dto.getVerificationCode());
        return success(updated, "Status updated successfully.");
    }

    @Operation(summary = "Send critical user action verification code")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @PostMapping("admin/users/{id}/critical-action/send-code")
    public ResponseEntity<TFUResponse<String>> sendCriticalUserActionCode(
            @PathVariable Long id,
            @Valid @RequestBody SendCriticalUserActionCodeRequest request) {
        UserModel actor = userService.findById(getCurrentUserId());
        if (actor == null) {
            return unauthorized("Not authenticated");
        }
        userService.sendCriticalUserActionCode(id, request.getEmail(), request.getActionType(), actor);
        return success("Verification code sent to your email.");
    }

    @Operation(summary = "Get critical role slot availability")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @GetMapping("admin/role-slots")
    public ResponseEntity<TFUResponse<CriticalRoleSlotsResponse>> getCriticalRoleSlots() {
        return success(userService.getCriticalRoleSlots());
    }

    @Operation(summary = "Delete user", description = "**USER_DETAILS_EDIT** — Admin/Director/BM by permission.")
    @PreAuthorize("@permissionChecker.has('USER_DETAILS_EDIT')")
    @DeleteMapping("admin/users/{id}")
    public ResponseEntity<TFUResponse<String>> deleteUser(
            @PathVariable Long id,
            @RequestBody(required = false) CriticalUserActionRequest request) {
        UserModel actor = userService.findById(getCurrentUserId());
        if (actor == null) {
            return unauthorized("Not authenticated");
        }
        if (request == null) {
            userService.deleteUser(id, actor);
        } else {
            userService.deleteUser(id, actor, request.getEmail(), request.getVerificationCode());
        }
        return success("Account deleted successfully.");
    }

    private UserDto toUserDto(UserModel user) {
        UserDto userDto = mapper.map(user, UserDto.class);
        if (user != null) {
            userDto.isActive = user.isActive();
            userDto.status = user.getStatus();
        }
        return userDto;
    }
}
