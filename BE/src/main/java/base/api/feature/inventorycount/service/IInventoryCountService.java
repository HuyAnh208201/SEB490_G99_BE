package base.api.feature.inventorycount.service;

import base.api.feature.inventorycount.dto.request.SubmitInventoryCountRequest;
import base.api.feature.inventorycount.dto.response.InventoryCountSessionResponse;
import base.api.feature.inventorycount.dto.response.InventoryCountSheetResponse;

import java.util.List;

/**
 * Nghiệp vụ kiểm kê hàng hóa và cập nhật tồn kho của nhân viên kho chi nhánh.
 */
public interface IInventoryCountService {

    InventoryCountSheetResponse getCountSheet();

    InventoryCountSessionResponse submitCount(SubmitInventoryCountRequest request);

    List<InventoryCountSessionResponse> getHistory();

    InventoryCountSessionResponse getSession(Long id);

    /** Duyệt phiên kiểm kê → cập nhật tồn kho chi nhánh theo số đếm thực tế. */
    InventoryCountSessionResponse approve(Long id);

    InventoryCountSessionResponse reject(Long id);
}
