package base.api.feature.warehouse.service;

import base.api.feature.warehouse.dto.response.WarehouseDashboardResponse;
import java.time.LocalDate;

public interface IWarehouseDashboardService {

    WarehouseDashboardResponse getDashboard();

    WarehouseDashboardResponse getDashboard(LocalDate from, LocalDate to);
}
