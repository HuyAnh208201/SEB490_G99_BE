package base.api.shared.security;

import base.api.shared.enums.UserRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Web permission matrix — power ladder: Admin/Director -> BM/WM -> Cashier/IS.
 */
public final class WebRolePermissions {

    private static final Map<UserRole, Set<WebPermission>> MATRIX = buildMatrix();

    private WebRolePermissions() {
    }

    private static Map<UserRole, Set<WebPermission>> buildMatrix() {
        Map<UserRole, Set<WebPermission>> map = new EnumMap<>(UserRole.class);

        map.put(UserRole.ADMIN, EnumSet.of(
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
                WebPermission.VIEW_SUPPLIER_RECEIPTS_PRICES,
                WebPermission.SUPPLIER_MANAGEMENT,
                WebPermission.MANAGE_BRANCH_IMPORT_REQUESTS,
                WebPermission.VIEW_CENTRAL_INVENTORY,
                WebPermission.VIEW_BRANCH_INVENTORY,
                WebPermission.REPORTS_VIEW
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
                WebPermission.MANAGE_BRANCH_IMPORT_REQUESTS,
                WebPermission.CATEGORY_MANAGEMENT,
                WebPermission.PRODUCT_MANAGEMENT,
                WebPermission.VIEW_SUPPLIER_RECEIPTS_PRICES,
                WebPermission.SUPPLIER_MANAGEMENT,
                WebPermission.VIEW_CENTRAL_INVENTORY,
                WebPermission.VIEW_BRANCH_INVENTORY,
                WebPermission.REPORTS_VIEW
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
                WebPermission.REFUND_APPROVAL,
                WebPermission.BRANCH_REVENUE_PROMOS,
                WebPermission.SHIFT_MANAGEMENT,
                WebPermission.CREATE_IMPORT_REQUEST,
                WebPermission.VIEW_BRANCH_INVENTORY,
                WebPermission.PRODUCT_MANAGEMENT,
                WebPermission.INVENTORY_COUNT,
                WebPermission.RECEIVE_SHIPMENT,
                WebPermission.SUPPLY_IMPORT_RECEIPT_APPROVE,
                WebPermission.REPORTS_VIEW
        ));

        map.put(UserRole.WAREHOUSE_MANAGER, EnumSet.of(
                WebPermission.WAREHOUSE_DASHBOARD,
                WebPermission.VIEW_CENTRAL_INVENTORY,
                WebPermission.MANAGE_BRANCH_IMPORT_REQUESTS,
                WebPermission.APPROVE_IMPORT_REQUEST,
                WebPermission.CHOOSE_EXTERNAL_SUPPLIER,
                WebPermission.VIEW_SUPPLIER_RECEIPTS_PRICES,
                WebPermission.SUPPLIER_MANAGEMENT,
                WebPermission.SET_RETAIL_PRICE,
                WebPermission.MANAGE_DISPATCH_ORDERS,
                WebPermission.PRODUCT_VIEW
        ));

        map.put(UserRole.INVENTORY_STAFF, EnumSet.of(
                WebPermission.VIEW_BRANCH_INVENTORY,
                WebPermission.PRODUCT_VIEW,
                WebPermission.RECEIVE_SHIPMENT,
                WebPermission.INVENTORY_COUNT,
                WebPermission.CASHIER_CLOSE_SHIFT,
                WebPermission.MY_SHIFTS
        ));

        map.put(UserRole.CASHIER, EnumSet.of(
                WebPermission.CASHIER_ADD_POINTS,
                WebPermission.CASHIER_CLOSE_SHIFT,
                WebPermission.MY_SHIFTS,
                WebPermission.POS_CHECKOUT,
                WebPermission.REFUND_REQUEST
        ));

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
