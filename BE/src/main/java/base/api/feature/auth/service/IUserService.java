package base.api.feature.auth.service;

import base.api.feature.auth.dto.request.ChangePasswordDto;
import base.api.feature.auth.dto.request.CompleteForgotPasswordDto;
import base.api.feature.auth.dto.request.CreateUserByAdminDto;
import base.api.feature.auth.dto.request.RegisterDto;
import base.api.feature.auth.dto.request.UpdateProfileDto;
import base.api.feature.auth.dto.response.CriticalRoleSlotsResponse;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.UserRole;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IUserService {
    UserModel createUser(UserModel model);
    UserModel findByUserName(String userName);
    boolean existedByEmail(String email);
    UserModel findById(Long id);

/**
 * Get or create a guest user from phone (walk-in POS customer).
 * Only phone + name are required; email and password are generated.
 *
 * @param fullName customer name entered by cashier; blank uses a default name
 */
UserModel getOrCreateGuestByPhone(String phone, String fullName);

    UserModel registerUser(RegisterDto dto);
    UserModel createUserByAdmin(CreateUserByAdminDto dto, UserModel creator) throws Exception;
    List<UserModel> getAllUsers();
    Page<UserModel> getUserPage(PageRequestDTO pageRequest, UserRole role, Long branchId, String status);
    InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo) throws Exception;

    InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo, String frontendBaseUrl) throws Exception;
    void completeForgotPassword(CompleteForgotPasswordDto dto) throws Exception;
    void verifyEmailByToken(String token) throws Exception;
    void resendVerificationEmail(String contactInfo) throws Exception;
    UserModel updateProfile(Long userId, UpdateProfileDto dto);
    void changePassword(Long userId, ChangePasswordDto dto) throws Exception;

    UserModel updateUserStatus(Long targetUserId, boolean active, UserModel actor);

    UserModel updateUserStatus(Long targetUserId, boolean active, UserModel actor, String email, String verificationCode);

    void deleteUser(Long targetUserId, UserModel actor);

    void deleteUser(Long targetUserId, UserModel actor, String email, String verificationCode);

    void sendCriticalUserActionCode(Long targetUserId, String email, String actionType, UserModel actor);

    CriticalRoleSlotsResponse getCriticalRoleSlots();
}
