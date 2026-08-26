package base.api.feature.posorder.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.request.CheckoutLineRequest;
import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.ApplicablePromotionResponse;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductCostService;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.CampaignBranchVisibility;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.entity.OrderDiscountModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.CampaignScope;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderCampaignTest {

    private static final Long BRANCH_ID = 10L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderDiscountRepository orderDiscountRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ProductCostService productCostService;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserService userService;
    @Mock private IBranchRepository branchRepository;
    @Mock private ICashierService cashierService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignBranchVisibility campaignBranchVisibility;
    @Mock private CurrentUserProvider currentUserProvider;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PosOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setFullName("Nguyen Thu Ngan");
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
        BranchModel branch = new BranchModel();
        branch.setId(BRANCH_ID);
        branch.setName("ChainStore Quan 1");
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(productCostService.unitCostForProduct(any())).thenReturn(BigDecimal.ZERO);
        when(shiftRepository
                .findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
                        anyLong(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.save(any())).thenAnswer(call -> {
            OrderModel order = call.getArgument(0);
            if (order.getId() == null) {
                order.setId(99L);
            }
            return order;
        });
        when(campaignBranchVisibility.isVisibleToBranch(any(), eq(BRANCH_ID))).thenReturn(true);
    }

    @Test
    void listApplicablePromotionsMarksMinOrderAndUnsupportedTypes() {
        CampaignModel percent = liveCampaign(1L, "10% Off", CampaignType.PERCENT, "10",
                "{\"minOrderAmount\":50000}");
        CampaignModel fixed = liveCampaign(2L, "20k Off", CampaignType.FIXED_AMOUNT, "20000", "{}");
        CampaignModel buy = liveCampaign(3L, "BXGY", CampaignType.BUY_X_GET_Y, "0",
                "{\"buyQuantity\":2,\"getQuantity\":1}");
        when(campaignRepository.findLiveByStatus(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(percent, fixed, buy));

        List<ApplicablePromotionResponse> result =
                service.listApplicablePromotions(new BigDecimal("40000"));

        assertEquals(3, result.size());

        ApplicablePromotionResponse percentRow = result.get(0);
        assertFalse(percentRow.isEligible());
        assertEquals(0, new BigDecimal("50000").compareTo(percentRow.getMinOrderAmount()));
        assertTrue(percentRow.getReason().toLowerCase().contains("minimum"));
        assertNull(percentRow.getDiscountAmount());

        ApplicablePromotionResponse fixedRow = result.get(1);
        assertTrue(fixedRow.isEligible());
        assertEquals(0, new BigDecimal("20000").compareTo(fixedRow.getDiscountAmount()));

        ApplicablePromotionResponse buyRow = result.get(2);
        assertFalse(buyRow.isEligible());
        assertTrue(buyRow.getReason().toLowerCase().contains("not supported"));
    }

    @Test
    void checkoutAppliesPercentCampaignAndEarnsPointsOnPostPromoTotal() {
        stubProduct(1, "Milk", "100000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);

        CampaignModel campaign = liveCampaign(7L, "10% Off", CampaignType.PERCENT, "10",
                "{\"minOrderAmount\":50000}");
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaign));

        UserModel customer = new UserModel();
        customer.setId(50L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setPoints(0L);
        customer.setPhone("0900000000");
        when(userService.getOrCreateGuestByPhone(eq("0900000000"), any())).thenReturn(customer);
        when(cashierService.settlePoints(eq(customer), any(BigDecimal.class), eq(0L)))
                .thenReturn(new ICashierService.PointSettlement(0L, 9L, 9L));

        CheckoutRequest request = cashRequest(1, 1, "100000");
        request.setCampaignId(7L);
        request.setCustomerPhone("0900000000");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("100000").compareTo(response.getSubtotal()));
        assertEquals(0, new BigDecimal("10000").compareTo(response.getDiscountAmount()));
        assertEquals(0, new BigDecimal("90000").compareTo(response.getTotal()));
        assertEquals(9L, response.getPointsEarned());

        ArgumentCaptor<OrderDiscountModel> discountCaptor = ArgumentCaptor.forClass(OrderDiscountModel.class);
        verify(orderDiscountRepository).save(discountCaptor.capture());
        assertEquals("CAMPAIGN:7", discountCaptor.getValue().getCode());
        assertEquals(0, new BigDecimal("10000").compareTo(discountCaptor.getValue().getDiscountAmount()));

        ArgumentCaptor<BigDecimal> earnBase = ArgumentCaptor.forClass(BigDecimal.class);
        verify(cashierService).settlePoints(eq(customer), earnBase.capture(), eq(0L));
        assertEquals(0, new BigDecimal("90000").compareTo(earnBase.getValue()));
    }

    @Test
    void checkoutRejectsIneligibleCampaign() {
        stubProduct(1, "Milk", "10000");
        CampaignModel campaign = liveCampaign(7L, "10% Off", CampaignType.PERCENT, "10",
                "{\"minOrderAmount\":50000}");
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaign));

        CheckoutRequest request = cashRequest(1, 1, "100000");
        request.setCampaignId(7L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkout(request));
        assertTrue(error.getMessage().toLowerCase().contains("minimum"));
    }

    @Test
    void chainSubsetCampaignHiddenFromOtherBranch() {
        CampaignModel campaign = liveCampaign(1L, "Store only", CampaignType.FIXED_AMOUNT, "5000", null);
        when(campaignRepository.findLiveByStatus(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(campaign));
        when(campaignBranchVisibility.isVisibleToBranch(campaign, BRANCH_ID)).thenReturn(false);

        List<ApplicablePromotionResponse> result =
                service.listApplicablePromotions(new BigDecimal("100000"));

        assertTrue(result.isEmpty());
    }

    private CampaignModel liveCampaign(
            Long id, String name, CampaignType type, String discountValue, String conditions) {
        CampaignModel campaign = new CampaignModel();
        campaign.setId(id);
        campaign.setName(name);
        campaign.setType(type);
        campaign.setDiscountValue(new BigDecimal(discountValue));
        campaign.setConditions(conditions);
        campaign.setScope(CampaignScope.CHAIN);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setPriority(0);
        campaign.setStartAt(LocalDateTime.now().minusDays(1));
        campaign.setEndAt(LocalDateTime.now().plusDays(1));
        return campaign;
    }

    private void stubProduct(int id, String name, String price) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setName(name);
        product.setDefaultSalePrice(new BigDecimal(price));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
    }

    private CheckoutRequest cashRequest(int productId, int qty, String cashReceived) {
        CheckoutRequest request = new CheckoutRequest();
        request.getLines().add(line(productId, qty));
        request.setPaymentMethod("CASH");
        request.setCashReceived(new BigDecimal(cashReceived));
        return request;
    }

    private CheckoutLineRequest line(int productId, int qty) {
        CheckoutLineRequest line = new CheckoutLineRequest();
        line.setProductId(productId);
        line.setQuantity(qty);
        return line;
    }
}
