package base.api.feature.posorder.service;

import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.shared.entity.OrderDiscountModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trả mã giảm giá về trạng thái dùng được khi đơn bị huỷ hoặc được duyệt hoàn.
 * Mã bị khoá ngay lúc chốt đơn, nên đơn không đi đến cùng mà không nhả mã thì
 * khách mất mã vĩnh viễn dù chưa hề hưởng ưu đãi.
 */
@Service
public class VoucherReleaseService {

    private static final Logger log = LoggerFactory.getLogger(VoucherReleaseService.class);

    @Autowired
    private OrderDiscountRepository orderDiscountRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    /**
     * Nhả mọi mã đã áp lên đơn. Dòng giảm giá không gắn voucher (giảm tay) bị bỏ qua.
     *
     * @return số mã thực sự được trả về active
     */
    @Transactional
    public int releaseForOrder(Long orderId) {
        int released = 0;
        for (OrderDiscountModel discount : orderDiscountRepository.findByOrderId(orderId)) {
            if (discount.getVoucherId() != null) {
                released += voucherRepository.markActive(discount.getVoucherId());
            }
        }
        if (released > 0) {
            log.info("Released {} discount code(s) back to active for order {}", released, orderId);
        }
        return released;
    }
}
