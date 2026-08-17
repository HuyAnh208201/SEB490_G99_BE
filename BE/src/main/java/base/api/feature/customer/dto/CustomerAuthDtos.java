package base.api.feature.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class CustomerAuthDtos {

    private CustomerAuthDtos() {
    }

    @Data
    public static class RegisterRequestOtp {
        @NotBlank
        private String phone;
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 2, max = 120)
        private String fullName;
        @NotBlank
        @Size(min = 8, max = 72)
        private String password;
    }

    @Data
    public static class RegisterVerify {
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 6, max = 6)
        private String otp;
    }

    @Data
    public static class LoginRequest {
        private String identifier;
        private String phone;
        @NotBlank
        private String password;

        public String resolvedIdentifier() {
            if (identifier != null && !identifier.isBlank()) {
                return identifier.trim();
            }
            return phone == null ? null : phone.trim();
        }
    }

    @Data
    public static class ForgotRequestOtp {
        @NotBlank
        private String email;
    }

    @Data
    public static class ForgotVerify {
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 6, max = 6)
        private String otp;
        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String phone;
        private String email;
        private String fullName;
        private Long points;
        private String tierCode;
        private String tierName;
    }

    @Data
    public static class MessageResponse {
        private boolean success = true;
        private String message;

        public static MessageResponse of(String message) {
            MessageResponse response = new MessageResponse();
            response.setMessage(message);
            return response;
        }
    }
}
