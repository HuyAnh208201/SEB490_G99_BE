package base.api.feature.purchaseorder.mapper;

import base.api.shared.entity.PurchaseOrderModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class PurchaseOrderMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String toOrderNumber(PurchaseOrderModel order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        LocalDate date = order.getCreatedAt() == null ? LocalDate.now() : order.getCreatedAt().toLocalDate();
        return "PO-" + date.format(DATE_FORMAT) + "-" + String.format("%06d", order.getId());
    }
}
