package base.api.shared.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRole {
    ADMIN,
    DIRECTOR,
    BRANCH_MANAGER,
    WAREHOUSE_MANAGER,
    INVENTORY_STAFF,
    CASHIER,
    OWNER,
    MANAGER,
    CUSTOMER,
    STAFF;

    public boolean isWebRole() {
        UserRole web = toWebRole();
        return web == ADMIN || web == DIRECTOR || web == BRANCH_MANAGER || web == WAREHOUSE_MANAGER;
    }

    public UserRole toWebRole() {
        return switch (this) {
            case MANAGER -> BRANCH_MANAGER;
            case OWNER -> DIRECTOR;
            default -> this;
        };
    }

    public boolean canManageUsers() {
        UserRole web = toWebRole();
        return web == ADMIN || web == DIRECTOR || web == BRANCH_MANAGER;
    }

    public boolean canAssignRole(UserRole targetRole) {
        if (targetRole == null) {
            return false;
        }
        UserRole creator = toWebRole();
        UserRole target = targetRole.toWebRole();

        if (creator == ADMIN) {
            return true;
        }
        if (creator == DIRECTOR) {
            return target != ADMIN;
        }
        if (creator == BRANCH_MANAGER) {
            return target == INVENTORY_STAFF || target == CASHIER || target == BRANCH_MANAGER;
        }
        return false;
    }
}
