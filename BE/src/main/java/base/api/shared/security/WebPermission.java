package base.api.shared.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Màn hình / module trên Web System (theo ma trận phân quyền).
 */
@Getter
@RequiredArgsConstructor
public enum WebPermission {
    SYSTEM_SETTINGS_MASTER_DATA("System Settings Master Data"),
    USER_MANAGEMENT_LIST("User Management List"),
    USER_DETAILS_EDIT("User Details / Edit"),
    BRANCH_LIST_ADMIN("Branch List Screen (Admin)"),
    MANAGE_BRANCH_INFORMATION("Manage Branch Information Screen"),
    DIRECTOR_DASHBOARD("Director Dashboard"),
    BRANCH_LIST_DIRECTOR("Branch List Screen (Director)"),
    PROMOTION_LIST("Promotion List Screen"),
    PROMOTION_DETAILS("Promotion Details Screen"),
    PROMOTION_MANAGEMENT("Promotion Management"),
    BUSINESS_PERFORMANCE_REPORTS("Business Performance Reports"),
    STRATEGIC_PLANNING_OVERVIEW("Strategic Planning Overview"),
    BRANCH_DASHBOARD("Branch Dashboard"),
    MANAGE_BRANCH_STAFF_INFO("Manage Branch/Staff Info"),
    APPROVE_CASH_DISCREPANCY("Approve Cash Discrepancy"),
    BRANCH_REVENUE_PROMOS("Branch Revenue/Promos"),
    SUPPLY_IMPORT_RECEIPT_APPROVE("Supply Import Receipt: Approve"),
    SHIFT_MANAGEMENT("Shift Management: Create/Assign"),
    CREATE_IMPORT_REQUEST("Create Import Request"),
    APPROVE_IMPORT_REQUEST("Approve Import Request"),
    WAREHOUSE_DASHBOARD("Warehouse Dashboard"),
    VIEW_CENTRAL_INVENTORY("View Central Inventory"),
    MANAGE_BRANCH_IMPORT_REQUESTS("Manage Branch Import Requests"),
    CHOOSE_EXTERNAL_SUPPLIER("Choose External Supplier"),
    VIEW_SUPPLIER_RECEIPTS_PRICES("View Supplier Receipts and Retail Prices"),
    SET_RETAIL_PRICE("Schedule Retail Price"),
    MANAGE_DISPATCH_ORDERS("Manage Dispatch Orders"),
    CATEGORY_MANAGEMENT("Category Management"),
    PRODUCT_MANAGEMENT("Product Management"),
    PRODUCT_VIEW("Product View (read-only)"),
    SUPPLIER_MANAGEMENT("Supplier Management"),
    VIEW_BRANCH_INVENTORY("View Branch Inventory"),
    RECEIVE_SHIPMENT("Receive Shipment"),
    INVENTORY_COUNT("Inventory Count"),
    CASHIER_ADD_POINTS("Cashier: Add loyalty points to customer"),
    CASHIER_CLOSE_SHIFT("Cashier/Staff: Close shift and submit actual cash"),
    MY_SHIFTS("My Shifts"),

    POS_CHECKOUT("Cashier: Sell at the counter, view order history and discount codes"),

    REFUND_REQUEST("Cashier: Request order refund"),
    REFUND_APPROVAL("Branch Manager: Approve order refund"),

    REPORTS_VIEW("View business reports");

    private final String label;
}
