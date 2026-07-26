package base.api.feature.branchreceiving.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.branchreceiving.dto.request.ReceiveShipmentRequest;
import base.api.feature.branchreceiving.dto.response.ReceiveShipmentDetailResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingHistoryResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingOrderResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingReceiptDetailResponse;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branchreceiving.service.IBranchReceivingService;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.DispatchOrderRequestModel;
import base.api.shared.entity.GoodsReceiptItemModel;
import base.api.shared.entity.GoodsReceiptModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.DispatchStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BranchReceivingServiceImpl implements IBranchReceivingService {

    private static final String STATUS_PENDING = "PENDING_APPROVAL";
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyy-MMdd");

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    @Autowired
    private DispatchOrderRequestRepository dispatchOrderRequestRepository;

    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;

    @Autowired
    private GoodsReceiptItemRepository goodsReceiptItemRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private DispatchMapper dispatchMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    public List<ReceivingOrderResponse> getIncomingOrders() {
        Long branchId = currentBranchId();
        List<PurchaseRequestModel> requests = purchaseRequestRepository.findByBranchIdAndStatusIn(
                branchId,
                List.of(PurchaseRequestStatus.DISPATCHING, PurchaseRequestStatus.IN_TRANSIT, PurchaseRequestStatus.RECEIVED));
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Long> requestIds = requests.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, Long> dispatchByRequest = dispatchOrderRequestRepository.findByPurchaseRequestIdIn(requestIds).stream()
                .collect(Collectors.toMap(
                        DispatchOrderRequestModel::getPurchaseRequestId,
                        DispatchOrderRequestModel::getDispatchOrderId,
                        (a, b) -> a));
        Map<Long, DispatchOrderModel> ordersById = dispatchOrderRepository
                .findAllById(new LinkedHashSet<>(dispatchByRequest.values())).stream()
                .collect(Collectors.toMap(DispatchOrderModel::getId, o -> o, (a, b) -> a));
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());

        List<ReceivingOrderResponse> rows = new ArrayList<>();
        for (PurchaseRequestModel pr : requests) {
            Long dispatchOrderId = dispatchByRequest.get(pr.getId());
            if (dispatchOrderId == null) {
                continue;
            }
            DispatchOrderModel order = ordersById.get(dispatchOrderId);
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());

            ReceivingOrderResponse row = new ReceivingOrderResponse();
            row.setDispatchOrderId(dispatchOrderId);
            row.setDispatchNumber(order == null ? null : dispatchMapper.toDispatchNumber(order));
            row.setRequestId(pr.getId());
            row.setRequestNumber(dispatchMapper.toRequestNumber(pr));
            row.setShipmentDate(order == null ? pr.getCreatedAt() : order.getCreatedAt());
            row.setProductCount(details.size());
            row.setCategories(distinctCategories(details, productsById));
            row.setStatus(mapTrackingStatus(order, pr.getStatus()));
            row.setCanReceive(pr.getStatus() == PurchaseRequestStatus.IN_TRANSIT
                    && order != null
                    && order.getStatus() == DispatchStatus.DELIVERING);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(
                ReceivingOrderResponse::getShipmentDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    @Override
    public Page<ReceivingOrderResponse> getIncomingOrderPage(PageRequestDTO pageRequest, String status) {
        String search = pageRequest.normalizedSearch();
        String normalizedSearch = search == null ? null : search.toLowerCase(Locale.ROOT);
        String normalizedStatus = normalize(status);

        List<ReceivingOrderResponse> filtered = getIncomingOrders().stream()
                .filter(row -> normalizedStatus == null
                        || normalizedStatus.equalsIgnoreCase(row.getStatus()))
                .filter(row -> normalizedSearch == null
                        || containsIgnoreCase(row.getDispatchNumber(), normalizedSearch)
                        || containsIgnoreCase(row.getRequestNumber(), normalizedSearch)
                        || row.getCategories().stream().anyMatch(category -> containsIgnoreCase(category, normalizedSearch)))
                .toList();

        Pageable pageable = pageRequest.toPageable();
        int from = Math.min((int) pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
    }

    @Override
    public ReceiveShipmentDetailResponse getShipmentDetail(Long dispatchOrderId, Long requestId) {
        Long branchId = currentBranchId();
        PurchaseRequestModel pr = loadBranchRequest(requestId, branchId);
        assertLinked(dispatchOrderId, requestId);

        DispatchOrderModel order = dispatchOrderRepository.findById(dispatchOrderId)
                .orElseThrow(() -> new NotFoundException("Dispatch order not found."));
        BranchModel branch = branchRepository.findById(branchId).orElse(null);

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(requestId);
        Map<Integer, ProductModel> productsById = loadProducts(List.of(details));

        ReceiveShipmentDetailResponse response = new ReceiveShipmentDetailResponse();
        response.setDispatchOrderId(dispatchOrderId);
        response.setDispatchNumber(dispatchMapper.toDispatchNumber(order));
        response.setRequestId(requestId);
        response.setRequestNumber(dispatchMapper.toRequestNumber(pr));
        response.setShipmentDate(order.getCreatedAt());
        response.setBranchId(branchId);
        response.setStoreName(branch == null ? null : branch.getName());
        response.setSource("Warehouse Stock");
        response.setStatus(receivingStatus(pr.getStatus()));
        response.setCanReceive(pr.getStatus() == PurchaseRequestStatus.IN_TRANSIT);

        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            ReceiveShipmentDetailResponse.Item item = new ReceiveShipmentDetailResponse.Item();
            item.setProductId(detail.getProductId());
            item.setProductCode(product == null ? null : product.getCode());
            item.setProductName(product == null ? null : product.getName());
            item.setUnit(product == null ? null : product.getUnit());
            item.setShippedQuantity(dispatchQuantity(detail));
            response.getItems().add(item);
        }
        return response;
    }

    @Override
    @Transactional
    public ReceivingHistoryResponse receiveShipment(Long dispatchOrderId, Long requestId, ReceiveShipmentRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("At least one item is required.");
        }
        UserModel staff = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(staff);
        PurchaseRequestModel pr = loadBranchRequest(requestId, branchId);
        assertLinked(dispatchOrderId, requestId);

        if (pr.getStatus() != PurchaseRequestStatus.IN_TRANSIT) {
            throw new BadRequestException("Only shipments in transit can be received.");
        }

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(requestId);
        Map<Integer, PurchaseRequestDetailModel> detailByProduct = details.stream()
                .collect(Collectors.toMap(PurchaseRequestDetailModel::getProductId, d -> d, (a, b) -> a));

        Map<Integer, ReceiveShipmentRequest.Item> inputByProduct = new HashMap<>();
        for (ReceiveShipmentRequest.Item item : request.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            if (!detailByProduct.containsKey(item.getProductId())) {
                throw new BadRequestException("Product " + item.getProductId() + " is not part of this shipment.");
            }
            inputByProduct.put(item.getProductId(), item);
        }

        GoodsReceiptModel receipt = new GoodsReceiptModel();
        receipt.setPurchaseRequestId(requestId);
        receipt.setDispatchOrderId(dispatchOrderId);
        receipt.setBranchId(branchId);
        receipt.setStockStaffId(staff.getId());
        receipt.setStatus(STATUS_PENDING);
        GoodsReceiptModel savedReceipt = goodsReceiptRepository.save(receipt);

        List<GoodsReceiptItemModel> receiptItems = new ArrayList<>();
        for (PurchaseRequestDetailModel detail : details) {
            ReceiveShipmentRequest.Item input = inputByProduct.get(detail.getProductId());
            int ordered = dispatchQuantity(detail);
            int received = input == null || input.getReceivedQuantity() == null ? ordered : safe(input.getReceivedQuantity());

            GoodsReceiptItemModel receiptItem = new GoodsReceiptItemModel();
            receiptItem.setGoodsReceiptId(savedReceipt.getId());
            receiptItem.setProductId(detail.getProductId());
            receiptItem.setOrderedQuantity(ordered);
            receiptItem.setReceivedQuantity(received);
            receiptItem.setNote(input == null ? null : normalize(input.getNote()));
            receiptItems.add(receiptItem);

            increaseBranchStock(branchId, detail.getProductId(), received);
        }
        goodsReceiptItemRepository.saveAll(receiptItems);

        pr.setStatus(PurchaseRequestStatus.RECEIVED);
        purchaseRequestRepository.save(pr);

        markDispatchReceivedIfComplete(dispatchOrderId);

        return buildHistoryRow(savedReceipt, receiptItems.size(), staff.getFullName(), pr);
    }

    @Override
    public List<ReceivingHistoryResponse> getReceivingHistory() {
        Long branchId = currentBranchId();
        List<GoodsReceiptModel> receipts = goodsReceiptRepository.findByBranchIdOrderByReceivedAtDesc(branchId);
        return buildHistoryRows(receipts);
    }

    @Override
    public Page<ReceivingHistoryResponse> getReceivingHistoryPage(PageRequestDTO pageRequest, String status) {
        Long branchId = currentBranchId();
        String search = pageRequest.normalizedSearch();
        String normalizedStatus = normalize(status);
        Long searchedId = extractNumericId(search);

        Specification<GoodsReceiptModel> spec = (root, query, cb) -> cb.equal(root.get("branchId"), branchId);
        if (normalizedStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("status")), normalizedStatus.toUpperCase(Locale.ROOT)));
        }
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> {
                var statusMatch = cb.like(cb.lower(root.get("status")), pattern);
                if (searchedId == null) {
                    return statusMatch;
                }
                return cb.or(
                        statusMatch,
                        cb.equal(root.get("id"), searchedId),
                        cb.equal(root.get("purchaseRequestId"), searchedId),
                        cb.equal(root.get("dispatchOrderId"), searchedId));
            });
        }

        Pageable pageable = pageRequest.toPageable(
                "receivedAt",
                Sort.Direction.DESC,
                Set.of("id", "receivedAt", "status"));
        Page<GoodsReceiptModel> receipts = goodsReceiptRepository.findAll(spec, pageable);
        return new PageImpl<>(buildHistoryRows(receipts.getContent()), pageable, receipts.getTotalElements());
    }

    private List<ReceivingHistoryResponse> buildHistoryRows(List<GoodsReceiptModel> receipts) {
        if (receipts.isEmpty()) {
            return List.of();
        }

        List<Long> receiptIds = receipts.stream().map(GoodsReceiptModel::getId).toList();
        Map<Long, Long> itemCountByReceipt = goodsReceiptItemRepository.findByGoodsReceiptIdIn(receiptIds).stream()
                .collect(Collectors.groupingBy(GoodsReceiptItemModel::getGoodsReceiptId, Collectors.counting()));

        Set<Long> requestIds = receipts.stream()
                .map(GoodsReceiptModel::getPurchaseRequestId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PurchaseRequestModel> requestsById = purchaseRequestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(PurchaseRequestModel::getId, r -> r, (a, b) -> a));
        Set<Long> dispatchIds = receipts.stream()
                .map(GoodsReceiptModel::getDispatchOrderId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DispatchOrderModel> ordersById = dispatchOrderRepository.findAllById(dispatchIds).stream()
                .collect(Collectors.toMap(DispatchOrderModel::getId, o -> o, (a, b) -> a));
        Map<Long, String> staffNames = staffNames(receipts);

        List<ReceivingHistoryResponse> rows = new ArrayList<>();
        for (GoodsReceiptModel receipt : receipts) {
            PurchaseRequestModel pr = requestsById.get(receipt.getPurchaseRequestId());
            DispatchOrderModel order = ordersById.get(receipt.getDispatchOrderId());

            ReceivingHistoryResponse row = new ReceivingHistoryResponse();
            row.setReceiptId(receipt.getId());
            row.setReceiptCode(receiptCode(receipt));
            row.setDispatchOrderId(receipt.getDispatchOrderId());
            row.setDispatchNumber(order == null ? null : dispatchMapper.toDispatchNumber(order));
            row.setRequestId(receipt.getPurchaseRequestId());
            row.setRequestNumber(pr == null ? null : dispatchMapper.toRequestNumber(pr));
            row.setReceivedAt(receipt.getReceivedAt());
            row.setProductCount(itemCountByReceipt.getOrDefault(receipt.getId(), 0L).intValue());
            row.setReceivedByName(staffNames.get(receipt.getStockStaffId()));
            row.setStatus(normalizeReceiptStatus(receipt.getStatus()));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public ReceivingReceiptDetailResponse getReceiptDetail(Long receiptId) {
        Long branchId = currentBranchId();
        GoodsReceiptModel receipt = goodsReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Receipt not found."));
        if (!branchId.equals(receipt.getBranchId())) {
            throw new ForbiddenException("Access denied.");
        }

        List<GoodsReceiptItemModel> items = goodsReceiptItemRepository.findByGoodsReceiptId(receiptId);
        Set<Integer> productIds = items.stream().map(GoodsReceiptItemModel::getProductId).collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = productIds.isEmpty() ? Map.of()
                : productRepository.findByIdInWithCategory(productIds).stream()
                        .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));

        PurchaseRequestModel pr = receipt.getPurchaseRequestId() == null ? null
                : purchaseRequestRepository.findById(receipt.getPurchaseRequestId()).orElse(null);
        DispatchOrderModel order = receipt.getDispatchOrderId() == null ? null
                : dispatchOrderRepository.findById(receipt.getDispatchOrderId()).orElse(null);
        BranchModel branch = branchRepository.findById(branchId).orElse(null);

        ReceivingReceiptDetailResponse response = new ReceivingReceiptDetailResponse();
        response.setReceiptId(receipt.getId());
        response.setReceiptCode(receiptCode(receipt));
        response.setDispatchNumber(order == null ? null : dispatchMapper.toDispatchNumber(order));
        response.setRequestNumber(pr == null ? null : dispatchMapper.toRequestNumber(pr));
        response.setReceivedAt(receipt.getReceivedAt());
        response.setReceivedByName(staffNames(List.of(receipt)).get(receipt.getStockStaffId()));
        response.setStoreName(branch == null ? null : branch.getName());
        response.setStatus(normalizeReceiptStatus(receipt.getStatus()));

        for (GoodsReceiptItemModel item : items) {
            ProductModel product = productsById.get(item.getProductId());
            ReceivingReceiptDetailResponse.Item line = new ReceivingReceiptDetailResponse.Item();
            line.setProductId(item.getProductId());
            line.setProductCode(product == null ? null : product.getCode());
            line.setProductName(product == null ? null : product.getName());
            line.setUnit(product == null ? null : product.getUnit());
            line.setOrderedQuantity(item.getOrderedQuantity());
            line.setReceivedQuantity(item.getReceivedQuantity());
            line.setNote(item.getNote());
            response.getItems().add(line);
        }
        return response;
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    private void markDispatchReceivedIfComplete(Long dispatchOrderId) {
        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(dispatchOrderId).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        if (requestIds.isEmpty()) {
            return;
        }
        boolean allReceived = purchaseRequestRepository.findAllById(requestIds).stream()
                .allMatch(pr -> pr.getStatus() == PurchaseRequestStatus.RECEIVED);
        if (!allReceived) {
            return;
        }
        dispatchOrderRepository.findById(dispatchOrderId).ifPresent(order -> {
            order.setStatus(DispatchStatus.RECEIVED);
            order.setDeliveredAt(LocalDateTime.now());
            dispatchOrderRepository.save(order);
        });
    }

    private ReceivingHistoryResponse buildHistoryRow(GoodsReceiptModel receipt, int productCount, String staffName, PurchaseRequestModel pr) {
        DispatchOrderModel order = receipt.getDispatchOrderId() == null ? null
                : dispatchOrderRepository.findById(receipt.getDispatchOrderId()).orElse(null);
        ReceivingHistoryResponse row = new ReceivingHistoryResponse();
        row.setReceiptId(receipt.getId());
        row.setReceiptCode(receiptCode(receipt));
        row.setDispatchOrderId(receipt.getDispatchOrderId());
        row.setDispatchNumber(order == null ? null : dispatchMapper.toDispatchNumber(order));
        row.setRequestId(receipt.getPurchaseRequestId());
        row.setRequestNumber(pr == null ? null : dispatchMapper.toRequestNumber(pr));
        row.setReceivedAt(receipt.getReceivedAt());
        row.setProductCount(productCount);
        row.setReceivedByName(staffName);
        row.setStatus(normalizeReceiptStatus(receipt.getStatus()));
        return row;
    }

    private Long currentBranchId() {
        return requireBranch(currentUserProvider.getCurrentUserOrThrow());
    }

    private Long requireBranch(UserModel user) {
        if (user.getBranchId() == null) {
            throw new ForbiddenException("Your account is not assigned to a branch.");
        }
        return user.getBranchId();
    }

    private PurchaseRequestModel loadBranchRequest(Long requestId, Long branchId) {
        PurchaseRequestModel pr = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Purchase request not found."));
        if (!branchId.equals(pr.getBranchId())) {
            throw new ForbiddenException("Access denied.");
        }
        return pr;
    }

    private void assertLinked(Long dispatchOrderId, Long requestId) {
        boolean linked = dispatchOrderRequestRepository.findByDispatchOrderId(dispatchOrderId).stream()
                .anyMatch(link -> link.getPurchaseRequestId().equals(requestId));
        if (!linked) {
            throw new NotFoundException("Request is not part of this dispatch order.");
        }
    }

    private Map<Long, List<PurchaseRequestDetailModel>> loadDetailsByRequest(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        return detailRepository.findByPurchaseRequestIdIn(requestIds).stream()
                .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
    }

    private Map<Integer, ProductModel> loadProducts(Iterable<List<PurchaseRequestDetailModel>> detailGroups) {
        Set<Integer> productIds = new LinkedHashSet<>();
        for (List<PurchaseRequestDetailModel> details : detailGroups) {
            for (PurchaseRequestDetailModel detail : details) {
                if (detail.getProductId() != null) {
                    productIds.add(detail.getProductId());
                }
            }
        }
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, String> staffNames(List<GoodsReceiptModel> receipts) {
        Set<Long> staffIds = receipts.stream()
                .map(GoodsReceiptModel::getStockStaffId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (staffIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(UserModel::getId, UserModel::getFullName, (a, b) -> a));
    }

    private List<String> distinctCategories(List<PurchaseRequestDetailModel> details, Map<Integer, ProductModel> productsById) {
        Set<String> categories = new LinkedHashSet<>();
        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            if (product != null && product.getCategory() != null && product.getCategory().getName() != null) {
                categories.add(product.getCategory().getName());
            }
        }
        return new ArrayList<>(categories);
    }

    private String mapTrackingStatus(DispatchOrderModel order, PurchaseRequestStatus requestStatus) {
        if (order != null && order.getStatus() != null) {
            if (order.getStatus() == DispatchStatus.RECEIVED) {
                return DispatchStatus.RECEIVED.name();
            }
            if (order.getStatus() == DispatchStatus.REDELIVERY) {
                return DispatchStatus.REDELIVERY.name();
            }
            if (order.getStatus() == DispatchStatus.DELIVERING) {
                return DispatchStatus.DELIVERING.name();
            }
            if (order.getStatus() == DispatchStatus.PREPARING) {
                return DispatchStatus.PREPARING.name();
            }
        }
        return receivingStatus(requestStatus);
    }

    private String receivingStatus(PurchaseRequestStatus status) {
        if (status == null) {
            return DispatchStatus.PREPARING.name();
        }
        return switch (status) {
            case DISPATCHING -> DispatchStatus.PREPARING.name();
            case IN_TRANSIT -> DispatchStatus.DELIVERING.name();
            case RECEIVED -> DispatchStatus.RECEIVED.name();
            default -> DispatchStatus.PREPARING.name();
        };
    }

    private String normalizeReceiptStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING;
        }
        String upper = status.trim().toUpperCase().replace(" ", "_");
        return switch (upper) {
            case "COMPLETED", "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            default -> STATUS_PENDING;
        };
    }

    private String receiptCode(GoodsReceiptModel receipt) {
        LocalDate date = receipt.getReceivedAt() == null ? LocalDate.now() : receipt.getReceivedAt().toLocalDate();
        return "RCP-" + date.format(RECEIPT_DATE) + "-" + String.format("%03d", receipt.getId());
    }

    private int dispatchQuantity(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
    }

    private void increaseBranchStock(Long branchId, Integer productId, int quantity) {
        if (branchId == null || productId == null || quantity <= 0) {
            return;
        }
        BranchInventoryModel inventory = branchInventoryRepository
                .findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> {
                    BranchInventoryModel created = new BranchInventoryModel();
                    created.setBranchId(branchId);
                    created.setProductId(productId);
                    created.setCurrentStock(0);
                    return created;
                });
        inventory.setCurrentStock(safe(inventory.getCurrentStock()) + quantity);
        branchInventoryRepository.save(inventory);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private Long extractNumericId(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
