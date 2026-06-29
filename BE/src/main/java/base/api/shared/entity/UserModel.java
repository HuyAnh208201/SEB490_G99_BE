package base.api.shared.entity;

import base.api.shared.enums.UserGender;
import base.api.shared.enums.UserRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_user_name", columnNames = "user_name"),
        @UniqueConstraint(name = "uq_user_email", columnNames = "email")
})
public class UserModel extends BaseModel {

        @Column(name = "user_name", unique = true)
        public String userName;

        public String phone;

        public String firstName;

        public UserGender gender;

        public String lastName;

        public LocalDateTime birthDate;

        public String avatar;

        public boolean isActive = true;

        public boolean isVerified = false;

        @Column(unique = true)
        public String email;

        public String password;

        @Enumerated(EnumType.STRING)
        @Column(length = 50)
        private UserRole role;

        @Column(nullable = false)
        private Long points = 0L;

}
