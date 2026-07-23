package base.api.feature.posscan.service.impl;

import base.api.feature.posscan.dto.request.PushScanEventRequest;
import base.api.feature.posscan.dto.response.ScanEventFeedResponse;
import base.api.feature.posscan.dto.response.ScanEventResponse;
import base.api.feature.posscan.repository.PosScanEventRepository;
import base.api.feature.posscan.service.IPosScanService;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.service.IProductService;
import base.api.shared.entity.PosScanEventModel;
import base.api.shared.entity.UserModel;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PosScanServiceImpl implements IPosScanService {

    /** Không trả lại mã cũ hơn mốc này, tránh máy bán hàng mở muộn nuốt mã từ ca trước. */
    private static final int MAX_EVENT_AGE_HOURS = 12;

    @Autowired
    private PosScanEventRepository scanEventRepository;

    @Autowired
    private IProductService productService;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public ProductResponse pushScanEvent(PushScanEventRequest request) {
        UserModel cashier = currentUserProvider.getCurrentUserOrThrow();

        // Kiểm tra ngay bằng luồng quét chuẩn: không thấy / ngừng bán / sai chi nhánh /
        // hết tồn đều ném lỗi ở đây, nên mã hỏng không bao giờ lọt vào hàng đợi.
        ProductResponse product = productService.scanByBarcode(request.getBarcode());

        PosScanEventModel event = new PosScanEventModel();
        event.setCashierUserId(cashier.getId());
        event.setBranchId(cashier.getBranchId());
        event.setBarcode(request.getBarcode().trim());
        event.setProductId(product.getId());
        event.setProductName(product.getName());
        event.setCreatedAt(LocalDateTime.now());
        scanEventRepository.save(event);

        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public ScanEventFeedResponse pollScanEvents(Long afterId) {
        UserModel cashier = currentUserProvider.getCurrentUserOrThrow();
        Long latestId = scanEventRepository.findLatestIdByCashierUserId(cashier.getId());

        // Lần hỏi đầu tiên: chỉ lấy con trỏ, không nuốt lại mã đã quét trước đó.
        if (afterId == null) {
            return new ScanEventFeedResponse(latestId, List.of());
        }

        List<ScanEventResponse> events = scanEventRepository
                .findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(
                        cashier.getId(),
                        afterId,
                        LocalDateTime.now().minusHours(MAX_EVENT_AGE_HOURS))
                .stream()
                .map(e -> new ScanEventResponse(
                        e.getId(),
                        e.getBarcode(),
                        e.getProductId(),
                        e.getProductName(),
                        e.getCreatedAt()))
                .toList();

        // latestId phải tính cả trường hợp mã mới nhất đã quá cũ nên bị lọc ra khỏi events,
        // nếu không con trỏ sẽ đứng yên và hỏi lại mãi.
        Long nextCursor = events.isEmpty()
                ? Math.max(afterId, latestId == null ? 0L : latestId)
                : events.get(events.size() - 1).getId();

        return new ScanEventFeedResponse(nextCursor, events);
    }
}
