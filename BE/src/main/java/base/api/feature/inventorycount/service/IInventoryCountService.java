package base.api.feature.inventorycount.service;

import base.api.feature.inventorycount.dto.request.SubmitInventoryCountRequest;
import base.api.feature.inventorycount.dto.response.InventoryCountSessionResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;
import base.api.feature.inventorycount.dto.response.InventoryCountSheetResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Nghiệp vụ kiểm kê hàng hóa và cập nhật tồn kho của nhân viên kho chi nhánh.
 */
public interface IInventoryCountService {

    InventoryCountSheetResponse getCountSheet();

    InventoryCountSheetResponse getCountSheet(PageRequestDTO pageRequest, Integer categoryId);

    InventoryCountSessionResponse submitCount(SubmitInventoryCountRequest request);

    List<InventoryCountSessionResponse> getHistory();

    Page<InventoryCountSessionResponse> getHistoryPage(
            PageRequestDTO pageRequest,
            String status,
            String discrepancy,
            LocalDate from,
            LocalDate to);

    InventoryCountSessionResponse getSession(Long id);
}
