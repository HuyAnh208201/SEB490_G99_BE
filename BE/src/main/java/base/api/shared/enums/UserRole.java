package base.api.shared.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRole {
    ADMIN,
    PROMOTION_DIRECTOR,
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
        return switch (web) {
            case ADMIN, DIRECTOR, BRANCH_MANAGER, WAREHOUSE_MANAGER, INVENTORY_STAFF, CASHIER -> true;
            default -> false;
        };
    }

    public UserRole toWebRole() {
        return switch (this) {
            case MANAGER -> BRANCH_MANAGER;
            case OWNER, PROMOTION_DIRECTOR -> DIRECTOR;
            default -> this;
        };
    }

    public boolean canManageUsers() {
        UserRole web = toWebRole();
        return web == ADMIN || web == DIRECTOR || web == BRANCH_MANAGER;
    }

    public boolean requiresBranch() {
        UserRole web = toWebRole();
        return web == BRANCH_MANAGER || this == INVENTORY_STAFF || this == CASHIER;
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
