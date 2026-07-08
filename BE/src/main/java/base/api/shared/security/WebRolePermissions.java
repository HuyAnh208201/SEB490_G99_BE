package base.api.shared.security;

import base.api.shared.enums.UserRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ma trận phân quyền Web System.
 */
public final class WebRolePermissions {

    private static final Map<UserRole, Set<WebPermission>> MATRIX = buildMatrix();

    private WebRolePermissions() {
    }

    private static Map<UserRole, Set<WebPermission>> buildMatrix() {
        Map<UserRole, Set<WebPermission>> map = new EnumMap<>(UserRole.class);

        map.put(UserRole.ADMIN, EnumSet.of(
                WebPermission.ADMIN_DASHBOARD,
                WebPermission.SYSTEM_SETTINGS_MASTER_DATA,
                WebPermission.USER_MANAGEMENT_LIST,
                WebPermission.USER_DETAILS_EDIT,
                WebPermission.BRANCH_LIST_ADMIN,
                WebPermission.MANAGE_BRANCH_INFORMATION,
                WebPermission.MANAGE_BRANCH_STAFF_INFO,
                WebPermission.PROMOTION_LIST,
                WebPermission.PROMOTION_DETAILS,
                WebPermission.PROMOTION_MANAGEMENT,
                WebPermission.CATEGORY_MANAGEMENT,
                WebPermission.PRODUCT_MANAGEMENT,
                WebPermission.SUPPLIER_MANAGEMENT,
                WebPermission.APPROVE_IMPORT_REQUEST
        ));

        map.put(UserRole.DIRECTOR, EnumSet.of(
                WebPermission.SYSTEM_SETTINGS_MASTER_DATA,
                WebPermission.USER_MANAGEMENT_LIST,
                WebPermission.USER_DETAILS_EDIT,
                WebPermission.BRANCH_LIST_ADMIN,
                WebPermission.MANAGE_BRANCH_INFORMATION,
                WebPermission.DIRECTOR_DASHBOARD,
                WebPermission.BRANCH_LIST_DIRECTOR,
                WebPermission.PROMOTION_LIST,
                WebPermission.PROMOTION_DETAILS,
                WebPermission.PROMOTION_MANAGEMENT,
                WebPermission.BUSINESS_PERFORMANCE_REPORTS,
                WebPermission.STRATEGIC_PLANNING_OVERVIEW,
                WebPermission.BRANCH_REVENUE_PROMOS,
                WebPermission.APPROVE_IMPORT_REQUEST
        ));

        map.put(UserRole.BRANCH_MANAGER, EnumSet.of(
                WebPermission.USER_MANAGEMENT_LIST,
                WebPermission.USER_DETAILS_EDIT,
                WebPermission.BRANCH_DASHBOARD,
                WebPermission.MANAGE_BRANCH_STAFF_INFO,
                WebPermission.PROMOTION_LIST,
                WebPermission.PROMOTION_DETAILS,
                WebPermission.PROMOTION_MANAGEMENT,
                WebPermission.APPROVE_CASH_DISCREPANCY,
                WebPermission.BRANCH_REVENUE_PROMOS,
                WebPermission.SUPPLY_IMPORT_RECEIPT_APPROVE,
                WebPermission.SHIFT_MANAGEMENT,
                WebPermission.CREATE_IMPORT_REQUEST
        ));

        map.put(UserRole.WAREHOUSE_MANAGER, EnumSet.of(
                WebPermission.WAREHOUSE_DASHBOARD,
                WebPermission.VIEW_CENTRAL_INVENTORY,
                WebPermission.MANAGE_BRANCH_IMPORT_REQUESTS,
                WebPermission.CHOOSE_EXTERNAL_SUPPLIER,
                WebPermission.MANAGE_DISPATCH_ORDERS,
                WebPermission.CATEGORY_MANAGEMENT,
                WebPermission.PRODUCT_MANAGEMENT
        ));

        map.put(UserRole.INVENTORY_STAFF, EnumSet.noneOf(WebPermission.class));
        map.put(UserRole.CASHIER, EnumSet.noneOf(WebPermission.class));

        return Collections.unmodifiableMap(map);
    }

    public static boolean isAllowed(UserRole role, WebPermission permission) {
        if (role == null || permission == null) {
            return false;
        }
        UserRole webRole = role.toWebRole();
        Set<WebPermission> permissions = MATRIX.get(webRole);
        return permissions != null && permissions.contains(permission);
    }

    public static Set<WebPermission> permissionsFor(UserRole role) {
        if (role == null) {
            return Set.of();
        }
        Set<WebPermission> permissions = MATRIX.get(role.toWebRole());
        return permissions == null ? Set.of() : Collections.unmodifiableSet(permissions);
    }

    public static Map<UserRole, Set<WebPermission>> matrix() {
        return MATRIX;
    }
}
