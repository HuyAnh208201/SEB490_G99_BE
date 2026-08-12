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
 * Returns voucher codes to usable when an order is cancelled or a refund is approved.
 * A code is locked at checkout, so an order that never completes would cost the
 * customer their code for good without them ever getting the discount.
 */
@Service
public class VoucherReleaseService {

    private static final Logger log = LoggerFactory.getLogger(VoucherReleaseService.class);

    @Autowired
    private OrderDiscountRepository orderDiscountRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    /**
     * Releases every code applied to the order. Discount rows with no voucher are skipped.
     *
     * @return how many codes actually returned to active
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
