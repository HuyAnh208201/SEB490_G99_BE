package base.api.feature.branchreceiving.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.branchreceiving.dto.request.ReceiveShipmentRequest;
import base.api.feature.branchreceiving.dto.response.ReceivingHistoryResponse;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.DispatchOrderRequestModel;
import base.api.shared.entity.GoodsReceiptItemModel;
import base.api.shared.entity.GoodsReceiptModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.DispatchStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BranchReceivingServiceImpl} receive / approve / reject paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchReceivingServiceImplTest {

    private static final Long BRANCH_ID = 5L;
    private static final Long DISPATCH_ID = 70L;
    private static final Long REQUEST_ID = 20L;
    private static final Long RECEIPT_ID = 30L;

    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestDetailRepository detailRepository;
    @Mock private DispatchOrderRepository dispatchOrderRepository;
    @Mock private DispatchOrderRequestRepository dispatchOrderRequestRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IProductRepository productRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private IUserRepository userRepository;
    @Mock private DispatchMapper dispatchMapper;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ProductPackagingService productPackagingService;

    @InjectMocks
    private BranchReceivingServiceImpl service;

    @Test
    void receiveShipmentRejectsEmptyItems() {
        asStaff();
        ReceiveShipmentRequest request = new ReceiveShipmentRequest();
        request.setItems(List.of());

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, request));

        assertEquals("At least one item is required.", error.getMessage());
    }

    @Test
    void receiveShipmentRejectsWhenNotInTransit() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.DISPATCHING);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 2)));

        assertEquals("Only shipments in transit can be received.", error.getMessage());
    }

    @Test
    void receiveShipmentRejectsProductNotInShipment() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(goodsReceiptRepository.existsByDispatchOrderIdAndPurchaseRequestIdAndStatus(
                DISPATCH_ID, REQUEST_ID, "PENDING_APPROVAL")).thenReturn(false);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(10, 2)));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(99, 1)));

        assertTrue(error.getMessage().contains("is not part of this shipment"));
    }

    @Test
    void receiveShipmentCreatesFinalReceipt() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(goodsReceiptRepository.existsByDispatchOrderIdAndPurchaseRequestIdAndStatus(
                DISPATCH_ID, REQUEST_ID, "PENDING_APPROVAL")).thenReturn(false);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(10, 2)));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(goodsReceiptRepository.save(any(GoodsReceiptModel.class))).thenAnswer(inv -> {
            GoodsReceiptModel saved = inv.getArgument(0);
            saved.setId(RECEIPT_ID);
            return saved;
        });
        when(dispatchOrderRepository.findById(DISPATCH_ID))
                .thenReturn(Optional.of(dispatchOrder(DispatchStatus.DELIVERING)));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response =
                service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 2));

        assertEquals(RECEIPT_ID, response.getReceiptId());
        assertEquals("APPROVED", response.getStatus());
        ArgumentCaptor<GoodsReceiptModel> receiptCaptor = ArgumentCaptor.forClass(GoodsReceiptModel.class);
        verify(goodsReceiptRepository).save(receiptCaptor.capture());
        assertEquals("APPROVED", receiptCaptor.getValue().getStatus());
        verify(goodsReceiptItemRepository).saveAll(any());
    }

    @Test
    void receiveShipmentFinalizesReceiptAndStockWithoutManagerApproval() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(10, 2)));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(goodsReceiptRepository.save(any(GoodsReceiptModel.class))).thenAnswer(inv -> {
            GoodsReceiptModel saved = inv.getArgument(0);
            saved.setId(RECEIPT_ID);
            return saved;
        });
        when(purchaseRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BranchInventoryModel inventory = new BranchInventoryModel();
        inventory.setBranchId(BRANCH_ID);
        inventory.setProductId(10);
        inventory.setCurrentStock(5);
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, 10))
                .thenReturn(Optional.of(inventory));
        when(dispatchOrderRepository.findById(DISPATCH_ID))
                .thenReturn(Optional.of(dispatchOrder(DispatchStatus.DELIVERING)));
        when(purchaseRequestRepository.findAllById(List.of(REQUEST_ID))).thenReturn(List.of(pr));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response =
                service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 2));

        assertEquals("APPROVED", response.getStatus());
        assertEquals(PurchaseRequestStatus.RECEIVED, pr.getStatus());
        assertEquals(53, inventory.getCurrentStock());
        verify(branchInventoryRepository).save(inventory);
        verify(purchaseRequestRepository).save(pr);
    }

    @Test
    void approveReceiptRejectsNonPending() {
        asStaff();
        GoodsReceiptModel receipt = receipt("APPROVED");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.approveReceipt(RECEIPT_ID));

        assertEquals("Only pending receipts can be approved.", error.getMessage());
    }

    @Test
    void approveReceiptRestocksAndMarksReceived() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));

        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));

        GoodsReceiptItemModel item = new GoodsReceiptItemModel();
        item.setGoodsReceiptId(RECEIPT_ID);
        item.setProductId(10);
        item.setReceivedQuantity(48);
        when(goodsReceiptItemRepository.findByGoodsReceiptId(RECEIPT_ID)).thenReturn(List.of(item));

        BranchInventoryModel inventory = new BranchInventoryModel();
        inventory.setBranchId(BRANCH_ID);
        inventory.setProductId(10);
        inventory.setCurrentStock(5);
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, 10))
                .thenReturn(Optional.of(inventory));
        when(branchInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(purchaseRequestRepository.findAllById(List.of(REQUEST_ID))).thenReturn(List.of(pr));
        when(dispatchOrderRepository.findById(DISPATCH_ID))
                .thenReturn(Optional.of(dispatchOrder(DispatchStatus.DELIVERING)));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response = service.approveReceipt(RECEIPT_ID);

        assertEquals("APPROVED", response.getStatus());
        assertEquals(PurchaseRequestStatus.RECEIVED, pr.getStatus());
        assertEquals(53, inventory.getCurrentStock());
        verify(branchInventoryRepository).save(inventory);
    }

    @Test
    void rejectReceiptRejectsNonPending() {
        asStaff();
        GoodsReceiptModel receipt = receipt("REJECTED");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.rejectReceipt(RECEIPT_ID));

        assertEquals("Only pending receipts can be rejected.", error.getMessage());
    }

    @Test
    void rejectReceiptMarksRejectedWithoutRestock() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(purchaseRequest(PurchaseRequestStatus.IN_TRANSIT)));
        when(goodsReceiptItemRepository.findByGoodsReceiptId(RECEIPT_ID)).thenReturn(List.of());
        when(dispatchOrderRepository.findById(DISPATCH_ID)).thenReturn(Optional.empty());
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response = service.rejectReceipt(RECEIPT_ID);

        assertEquals("REJECTED", response.getStatus());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void receiveShipmentRejectsUserWithoutBranch() {
        UserModel user = new UserModel();
        user.setId(3L);
        user.setBranchId(null);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 1)));

        assertEquals("Your account is not assigned to a branch.", error.getMessage());
    }

    @Test
    void getShipmentDetailRejectsUnlinkedRequest() {
        asStaff();
        when(purchaseRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(purchaseRequest(PurchaseRequestStatus.IN_TRANSIT)));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID)).thenReturn(List.of());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.getShipmentDetail(DISPATCH_ID, REQUEST_ID));

        assertEquals("Request is not part of this dispatch order.", error.getMessage());
    }

    @Test
    void receiveShipmentRejectsNullItems() {
        asStaff();
        ReceiveShipmentRequest request = new ReceiveShipmentRequest();
        request.setItems(null);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, request));

        assertEquals("At least one item is required.", error.getMessage());
    }

    @Test
    void receiveShipmentRejectsNullRequest() {
        asStaff();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, null));

        assertEquals("At least one item is required.", error.getMessage());
    }

    @Test
    void receiveShipmentRejectsUnlinkedDispatchRequest() {
        asStaff();
        when(purchaseRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(purchaseRequest(PurchaseRequestStatus.IN_TRANSIT)));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID)).thenReturn(List.of());

        NotFoundException error = assertThrows(
                NotFoundException.class,
                () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 1)));

        assertEquals("Request is not part of this dispatch order.", error.getMessage());
    }

    @Test
    void receiveShipmentDeniesWrongBranch() {
        asStaff();
        PurchaseRequestModel otherBranch = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        otherBranch.setBranchId(99L);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(otherBranch));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 1)));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void receiveShipmentAllowsZeroReceivedQty() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(goodsReceiptRepository.existsByDispatchOrderIdAndPurchaseRequestIdAndStatus(
                DISPATCH_ID, REQUEST_ID, "PENDING_APPROVAL")).thenReturn(false);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(10, 2)));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(productPackagingService.toBaseQty(eq(0), any(ProductModel.class))).thenReturn(0);
        when(goodsReceiptRepository.save(any(GoodsReceiptModel.class))).thenAnswer(inv -> {
            GoodsReceiptModel saved = inv.getArgument(0);
            saved.setId(RECEIPT_ID);
            return saved;
        });
        when(dispatchOrderRepository.findById(DISPATCH_ID))
                .thenReturn(Optional.of(dispatchOrder(DispatchStatus.DELIVERING)));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response =
                service.receiveShipment(DISPATCH_ID, REQUEST_ID, receiveRequest(10, 0));

        assertEquals(RECEIPT_ID, response.getReceiptId());
        verify(productPackagingService).toBaseQty(eq(0), any(ProductModel.class));
        verify(goodsReceiptItemRepository).saveAll(any());
    }

    @Test
    void receiveShipmentSkipsNullProductIdItems() {
        asStaff();
        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(goodsReceiptRepository.existsByDispatchOrderIdAndPurchaseRequestIdAndStatus(
                DISPATCH_ID, REQUEST_ID, "PENDING_APPROVAL")).thenReturn(false);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(10, 2)));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(goodsReceiptRepository.save(any(GoodsReceiptModel.class))).thenAnswer(inv -> {
            GoodsReceiptModel saved = inv.getArgument(0);
            saved.setId(RECEIPT_ID);
            return saved;
        });
        when(dispatchOrderRepository.findById(DISPATCH_ID))
                .thenReturn(Optional.of(dispatchOrder(DispatchStatus.DELIVERING)));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceiveShipmentRequest request = new ReceiveShipmentRequest();
        ReceiveShipmentRequest.Item nullProduct = new ReceiveShipmentRequest.Item();
        nullProduct.setProductId(null);
        nullProduct.setReceivedQuantity(1);
        ReceiveShipmentRequest.Item valid = new ReceiveShipmentRequest.Item();
        valid.setProductId(10);
        valid.setReceivedQuantity(2);
        request.setItems(List.of(nullProduct, valid));

        ReceivingHistoryResponse response = service.receiveShipment(DISPATCH_ID, REQUEST_ID, request);

        assertEquals("APPROVED", response.getStatus());
        verify(goodsReceiptItemRepository).saveAll(any());
    }

    @Test
    void approveReceiptThrowsWhenReceiptMissing() {
        asStaff();
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.approveReceipt(RECEIPT_ID));

        assertEquals("Receipt not found.", error.getMessage());
    }

    @Test
    void approveReceiptDeniesOtherBranch() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        receipt.setBranchId(99L);
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.approveReceipt(RECEIPT_ID));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void approveReceiptRejectsWhenPrNotAwaitingApproval() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));
        when(purchaseRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(purchaseRequest(PurchaseRequestStatus.RECEIVED)));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.approveReceipt(RECEIPT_ID));

        assertEquals("Purchase request is not awaiting receipt approval.", error.getMessage());
    }

    @Test
    void approveReceiptThrowsWhenPurchaseRequestMissing() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.approveReceipt(RECEIPT_ID));

        assertEquals("Purchase request not found.", error.getMessage());
    }

    @Test
    void approveReceiptMarksDispatchReceivedWhenComplete() {
        asStaff();
        GoodsReceiptModel receipt = receipt("PENDING_APPROVAL");
        when(goodsReceiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(receipt));

        PurchaseRequestModel pr = purchaseRequest(PurchaseRequestStatus.IN_TRANSIT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pr));
        when(goodsReceiptItemRepository.findByGoodsReceiptId(RECEIPT_ID)).thenReturn(List.of());
        when(goodsReceiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(DISPATCH_ID))
                .thenReturn(List.of(link(DISPATCH_ID, REQUEST_ID)));
        when(purchaseRequestRepository.findAllById(List.of(REQUEST_ID))).thenReturn(List.of(pr));
        DispatchOrderModel dispatch = dispatchOrder(DispatchStatus.DELIVERING);
        when(dispatchOrderRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(dispatch));
        when(dispatchOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");

        ReceivingHistoryResponse response = service.approveReceipt(RECEIPT_ID);

        assertEquals("APPROVED", response.getStatus());
        ArgumentCaptor<DispatchOrderModel> dispatchCaptor = ArgumentCaptor.forClass(DispatchOrderModel.class);
        verify(dispatchOrderRepository).save(dispatchCaptor.capture());
        assertEquals(DispatchStatus.RECEIVED, dispatchCaptor.getValue().getStatus());
    }

    private void asStaff() {
        UserModel user = new UserModel();
        user.setId(3L);
        user.setBranchId(BRANCH_ID);
        user.setFullName("Stock Staff");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
    }

    private static ReceiveShipmentRequest receiveRequest(Integer productId, Integer qty) {
        ReceiveShipmentRequest request = new ReceiveShipmentRequest();
        ReceiveShipmentRequest.Item item = new ReceiveShipmentRequest.Item();
        item.setProductId(productId);
        item.setReceivedQuantity(qty);
        request.setItems(List.of(item));
        return request;
    }

    private static PurchaseRequestModel purchaseRequest(PurchaseRequestStatus status) {
        PurchaseRequestModel pr = new PurchaseRequestModel();
        pr.setId(REQUEST_ID);
        pr.setBranchId(BRANCH_ID);
        pr.setStatus(status);
        return pr;
    }

    private static PurchaseRequestDetailModel detail(Integer productId, Integer qty) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(REQUEST_ID);
        detail.setProductId(productId);
        detail.setApprovedQuantity(qty);
        detail.setRequestedQty(qty);
        return detail;
    }

    private static DispatchOrderRequestModel link(Long dispatchId, Long requestId) {
        DispatchOrderRequestModel link = new DispatchOrderRequestModel();
        link.setDispatchOrderId(dispatchId);
        link.setPurchaseRequestId(requestId);
        return link;
    }

    private static DispatchOrderModel dispatchOrder(DispatchStatus status) {
        DispatchOrderModel order = new DispatchOrderModel();
        order.setId(DISPATCH_ID);
        order.setStatus(status);
        return order;
    }

    private static GoodsReceiptModel receipt(String status) {
        GoodsReceiptModel receipt = new GoodsReceiptModel();
        receipt.setId(RECEIPT_ID);
        receipt.setBranchId(BRANCH_ID);
        receipt.setPurchaseRequestId(REQUEST_ID);
        receipt.setDispatchOrderId(DISPATCH_ID);
        receipt.setStockStaffId(3L);
        receipt.setStatus(status);
        return receipt;
    }

    private static ProductModel product(Integer id) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setCode("P" + id);
        product.setName("Product " + id);
        product.setUnit("bottle");
        return product;
    }
}
