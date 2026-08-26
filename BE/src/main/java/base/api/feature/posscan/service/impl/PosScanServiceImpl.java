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
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
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
    @Transactional(noRollbackFor = {
            NotFoundException.class,
            BadRequestException.class,
            BusinessException.class,
            ForbiddenException.class
    })
    public ProductResponse pushScanEvent(PushScanEventRequest request) {
        UserModel cashier = currentUserProvider.getCurrentUserOrThrow();
        String barcode = request.getBarcode().trim();

        try {
            // Kiểm tra ngay bằng luồng quét chuẩn: không thấy / ngừng bán / sai chi nhánh /
            // hết tồn đều ném lỗi — vẫn ghi sự kiện lỗi vào hàng đợi cho máy bán hàng.
            ProductResponse product = productService.scanByBarcode(barcode);

            PosScanEventModel event = new PosScanEventModel();
            event.setCashierUserId(cashier.getId());
            event.setBranchId(cashier.getBranchId());
            event.setBarcode(barcode);
            event.setProductId(product.getId());
            event.setProductName(product.getName());
            event.setErrorMessage(null);
            event.setCreatedAt(LocalDateTime.now());
            scanEventRepository.save(event);

            return product;
        } catch (NotFoundException | BadRequestException | BusinessException | ForbiddenException ex) {
            saveErrorEvent(cashier, barcode, ex.getMessage());
            throw ex;
        }
    }

    private void saveErrorEvent(UserModel cashier, String barcode, String errorMessage) {
        PosScanEventModel event = new PosScanEventModel();
        event.setCashierUserId(cashier.getId());
        event.setBranchId(cashier.getBranchId());
        event.setBarcode(barcode);
        event.setProductId(null);
        event.setProductName(null);
        event.setErrorMessage(truncate(errorMessage, 500));
        event.setCreatedAt(LocalDateTime.now());
        scanEventRepository.save(event);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
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
                .map(this::toResponse)
                .toList();

        // latestId phải tính cả trường hợp mã mới nhất đã quá cũ nên bị lọc ra khỏi events,
        // nếu không con trỏ sẽ đứng yên và hỏi lại mãi.
        Long nextCursor = events.isEmpty()
                ? Math.max(afterId, latestId == null ? 0L : latestId)
                : events.get(events.size() - 1).getId();

        return new ScanEventFeedResponse(nextCursor, events);
    }

    private ScanEventResponse toResponse(PosScanEventModel e) {
        boolean success = e.getErrorMessage() == null && e.getProductId() != null;
        return new ScanEventResponse(
                e.getId(),
                e.getBarcode(),
                e.getProductId(),
                e.getProductName(),
                e.getCreatedAt(),
                e.getErrorMessage(),
                success);
    }
}
