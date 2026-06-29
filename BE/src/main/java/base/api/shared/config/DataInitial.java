package base.api.shared.config;

import base.api.feature.auth.repository.IUserRepository;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.DependsOn("roleColumnMigration")
public class DataInitial {

    private static final Logger log = LoggerFactory.getLogger(DataInitial.class);

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        seedOrUpdateUser("admin", "admin@chainstore.com", "admin123", "Admin", "0900000001", UserRole.ADMIN);
        seedOrUpdateUser("director", "director@chainstore.com", "director123", "Director", "0900000002", UserRole.DIRECTOR);
        seedOrUpdateUser("branch_manager", "branch@chainstore.com", "branch123", "Branch", "0900000003", UserRole.BRANCH_MANAGER);
        seedOrUpdateUser("warehouse_manager", "warehouse@chainstore.com", "warehouse123", "Warehouse", "0900000004", UserRole.WAREHOUSE_MANAGER);
        seedOrUpdateUser("manager", "manager@chainstore.com", "manager123", "Manager", "0900000005", UserRole.MANAGER);
    }

    /**
     * Tạo hoặc cập nhật tài khoản seed — luôn đồng bộ role/mật khẩu khi restart BE.
     */
    private void seedOrUpdateUser(String userName, String email, String rawPassword,
                                  String firstName, String phone, UserRole role) {
        try {
            UserModel user = userRepository.findByUserName(userName).orElse(null);
            if (user == null) {
                user = userRepository.findByEmail(email).orElse(null);
            }
            if (user == null) {
                user = new UserModel();
                user.setLastName("ChainStore");
            }

            user.setUserName(userName);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setFirstName(firstName);
            user.setPhone(phone);
            user.setRole(role);
            user.setVerified(true);
            user.setActive(true);

            userRepository.save(user);
            log.info("Seed user ready: {} ({})", userName, role);
        } catch (Exception ex) {
            log.warn("Skipped seeding user '{}' ({}): {}", userName, email, ex.getMessage());
        }
    }
}
