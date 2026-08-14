package base.api.shared.entity;

import base.api.shared.enums.UserGender;
import base.api.shared.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@EqualsAndHashCode(callSuper = false)
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email", columnNames = "email")
})
public class UserModel extends BaseModel {

        @Transient
        public String userName;

        public String phone;

        @Transient
        public String firstName;

        @Convert(converter = base.api.shared.enums.UserGenderConverter.class)
        @Column(name = "gender", length = 32)
        public UserGender gender;

        @Transient
        public String lastName;

        @Column(name = "date_of_birth")
        public LocalDateTime birthDate;

        @Transient
        public String avatar;

        @Column(name = "is_verified", nullable = false)
        public boolean isVerified = true;

        @Column(unique = true)
        public String email;

        @Column(name = "password_hash", nullable = false)
        public String password;

        @Column(name = "full_name", nullable = false)
        private String fullName;

        @Column(nullable = false)
        private String status = "active";

        @Column(name = "branch_id")
        private Long branchId;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "role_id", nullable = false)
        private RoleModel roleEntity;

        @Column(name = "points", nullable = false)
        private Long points = 0L;

        /** Shared with customer app — membership_tiers.id. */
        @Column(name = "membership_tier_id")
        private Long membershipTierId;

        public String getUserName() {
                return email;
        }

        public void setUserName(String userName) {
                this.userName = userName;
                if (this.email == null || this.email.isBlank()) {
                        this.email = userName;
                }
        }

        public String getFirstName() {
                ensureNameParts();
                return firstName;
        }

        public void setFirstName(String firstName) {
                this.firstName = firstName;
                rebuildFullName();
        }

        public String getLastName() {
                ensureNameParts();
                return lastName;
        }

        public void setLastName(String lastName) {
                this.lastName = lastName;
                rebuildFullName();
        }

        @PostLoad
        private void splitFullName() {
                ensureNameParts();
        }

        private void ensureNameParts() {
                boolean missingFirst = firstName == null || firstName.isBlank();
                boolean missingLast = lastName == null || lastName.isBlank();
                if ((!missingFirst || !missingLast) || fullName == null || fullName.isBlank()) {
                        return;
                }
                String trimmed = fullName.trim();
                int space = trimmed.indexOf(' ');
                if (space < 0) {
                        this.firstName = trimmed;
                        this.lastName = "";
                } else {
                        this.firstName = trimmed.substring(0, space);
                        this.lastName = trimmed.substring(space + 1).trim();
                }
        }

        @JsonProperty("isActive")
        public boolean isActive() {
                return "active".equalsIgnoreCase(status);
        }

        public void setActive(boolean active) {
                this.status = active ? "active" : "locked";
        }

        public boolean isVerified() {
                return isVerified;
        }

        public void setVerified(boolean verified) {
                this.isVerified = verified;
        }

        public UserRole getRole() {
                if (roleEntity == null || roleEntity.getName() == null) {
                        return null;
                }
                return UserRole.valueOf(roleEntity.getName());
        }

        public void setRole(UserRole role) {
                if (role == null) {
                        this.roleEntity = null;
                        return;
                }
                RoleModel model = new RoleModel();
                model.setId(roleId(role));
                model.setName(role.name());
                this.roleEntity = model;
        }

        private void rebuildFullName() {
                String first = firstName == null ? "" : firstName.trim();
                String last = lastName == null ? "" : lastName.trim();
                String name = (first + " " + last).trim();
                if (!name.isBlank()) {
                        this.fullName = name;
                }
        }

        private Long roleId(UserRole role) {
                return switch (role.toWebRole()) {
                        case ADMIN -> 1L;
                        case DIRECTOR -> 2L;
                        case BRANCH_MANAGER -> 3L;
                        case WAREHOUSE_MANAGER -> 4L;
                        case INVENTORY_STAFF -> 5L;
                        case CASHIER -> 6L;
                        case CUSTOMER -> 7L;
                        default -> 7L;
                };
        }

}
