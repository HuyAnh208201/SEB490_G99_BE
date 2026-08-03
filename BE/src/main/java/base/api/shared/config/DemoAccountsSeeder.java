package base.api.shared.config;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.security.DemoAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures two demo staff accounts exist for local/demo use.
 * Password is always reset to {@link DemoAccounts#DEMO_PASSWORD} on startup so demos stay predictable.
 */
@Component
@Order(50)
public class DemoAccountsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsSeeder.class);

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            ensure(DemoAccounts.DEMO_CASHIER_EMAIL, "Demo Cashier", UserRole.CASHIER);
            ensure(DemoAccounts.DEMO_IS_EMAIL, "Demo Inventory Staff", UserRole.INVENTORY_STAFF);
            log.info("Demo accounts ensured (password {}).", DemoAccounts.DEMO_PASSWORD);
        } catch (Exception ex) {
            log.warn("Demo account seed skipped: {}", ex.getMessage());
        }
    }

    private void ensure(String email, String fullName, UserRole role) {
        RoleModel roleEntity = roleRepository.findByName(role.name())
                .orElseThrow(() -> new IllegalStateException("Missing role " + role.name()));
        UserModel user = userRepository.findByEmail(email).orElseGet(UserModel::new);
        boolean isNew = user.getId() == null;
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhone(isNew ? (role == UserRole.CASHIER ? "0900000091" : "0900000092") : user.getPhone());
        user.setPassword(passwordEncoder.encode(DemoAccounts.DEMO_PASSWORD));
        user.setRoleEntity(roleEntity);
        user.setBranchId(DemoAccounts.DEMO_BRANCH_ID);
        user.setActive(true);
        userRepository.save(user);
    }
}
