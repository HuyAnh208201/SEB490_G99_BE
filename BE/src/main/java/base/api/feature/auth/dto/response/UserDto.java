package base.api.feature.auth.dto.response;

import base.api.shared.enums.UserGender;
import base.api.shared.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;

    public String userName;

    public String phone;

    public String firstName;

    public UserGender gender;

    public String lastName;

    public LocalDateTime birthDate;

    public String avatar;

    public boolean isActive = true;

    /** Persisted account status: active | locked */
    public String status;

    public String email;


    private UserRole role;

    private Long branchId;

    private Long points;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
