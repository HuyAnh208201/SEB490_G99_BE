package base.api.feature.dispatch.mapper;

import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.PurchaseRequestModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class DispatchMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String toRequestNumber(PurchaseRequestModel request) {
        if (request == null || request.getId() == null) {
            return null;
        }
        LocalDate date = request.getCreatedAt() == null ? LocalDate.now() : request.getCreatedAt().toLocalDate();
        return "REQ-" + date.format(DATE_FORMAT) + "-" + String.format("%06d", request.getId());
    }

    public String toDispatchNumber(DispatchOrderModel order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        LocalDate date = order.getCreatedAt() == null ? LocalDate.now() : order.getCreatedAt().toLocalDate();
        return "DSP-" + date.format(DATE_FORMAT) + "-" + String.format("%06d", order.getId());
    }
}
