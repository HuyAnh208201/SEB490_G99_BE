package base.api.feature.auth.service;

import base.api.feature.auth.dto.request.ChangePasswordDto;
import base.api.feature.auth.dto.request.CompleteForgotPasswordDto;
import base.api.feature.auth.dto.request.CreateUserByAdminDto;
import base.api.feature.auth.dto.request.RegisterDto;
import base.api.feature.auth.dto.request.UpdateProfileDto;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.shared.entity.UserModel;

import java.util.List;

public interface IUserService {
    UserModel createUser(UserModel model);
    UserModel findByUserName(String userName);
    boolean existedByEmail(String email);
    UserModel findById(Long id);

    /**
     * Lấy hoặc tạo user guest từ SĐT (cho khách vãng lai).
     * User chỉ có phone, userName=phone, chưa verify.
     */
    UserModel getOrCreateGuestByPhone(String phone);

    UserModel registerUser(RegisterDto dto);
    UserModel createUserByAdmin(CreateUserByAdminDto dto, UserModel creator) throws Exception;
    List<UserModel> getAllUsers();
    InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo) throws Exception;
    void completeForgotPassword(CompleteForgotPasswordDto dto) throws Exception;
    void verifyEmailByToken(String token) throws Exception;
    void resendVerificationEmail(String contactInfo) throws Exception;
    UserModel updateProfile(Long userId, UpdateProfileDto dto);
    void changePassword(Long userId, ChangePasswordDto dto) throws Exception;
}
