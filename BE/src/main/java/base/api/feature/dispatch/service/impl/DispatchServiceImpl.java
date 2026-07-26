package base.api.feature.dispatch.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchApprovedRequestResponse;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.dispatch.service.IDispatchService;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.DispatchOrderRequestModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.DispatchStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.dto.PageRequestDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.function.Function;

@Service
public class DispatchServiceImpl implements IDispatchService {

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    @Autowired
    private DispatchOrderRequestRepository dispatchOrderRequestRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private DispatchMapper dispatchMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @Autowired
    private ProductPackagingService productPackagingService;

    @Override
    public List<DispatchApprovedRequestResponse> getApprovedRequests() {
        List<PurchaseRequestModel> requests = warehouseStockAllocationHelper.filterDispatchableApproved(
                purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED));
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Long> requestIds = requests.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        Map<Long, BranchModel> branchesById = loadBranches(requests);

        List<DispatchApprovedRequestResponse> result = new ArrayList<>();
        for (PurchaseRequestModel request : requests) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(request.getId(), List.of());
            BranchModel branch = branchesById.get(request.getBranchId());

            DispatchApprovedRequestResponse response = new DispatchApprovedRequestResponse();
            response.setId(request.getId());
            response.setRequestNumber(dispatchMapper.toRequestNumber(request));
            response.setBranchId(request.getBranchId());
            response.setBranchName(branch == null ? null : branch.getName());
            response.setArea(branch == null ? null : branch.getArea());
            response.setRoute(branch == null ? null : branch.getRoute());
            response.setItemCount(details.size());
            response.setCategories(distinctCategories(details, productsById));
            response.setCreatedAt(request.getCreatedAt());
            result.add(response);
        }
        result.sort(Comparator.comparing(
                DispatchApprovedRequestResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    public Page<DispatchApprovedRequestResponse> getApprovedRequestPage(
            PageRequestDTO pageRequest,
            String area,
            String route
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        String search = query.normalizedSearch();
        List<DispatchApprovedRequestResponse> candidates = getApprovedRequests().stream()
                .filter(row -> area == null || area.isBlank() || equalsIgnoreCase(row.getArea(), area))
                .filter(row -> route == null || route.isBlank() || equalsIgnoreCase(row.getRoute(), route))
                .filter(row -> search == null || matchesApprovedSearch(row, search))
                .toList();
        Map<Long, DispatchApprovedRequestResponse> responseById = candidates.stream()
                .collect(Collectors.toMap(DispatchApprovedRequestResponse::getId, Function.identity()));
        Specification<PurchaseRequestModel> specification = responseById.isEmpty()
                ? (root, ignored, cb) -> cb.disjunction()
                : (root, ignored, cb) -> root.get("id").in(responseById.keySet());
        return purchaseRequestRepository.findAll(
                        specification,
                        query.toPageable("createdAt", Sort.Direction.ASC, Set.of("id", "createdAt")))
                .map(request -> responseById.get(request.getId()));
    }

    @Override
    @Transactional
    public DispatchOrderResponse createDispatchOrder(CreateDispatchOrderRequest request) {
        if (request == null || request.getRequestIds() == null || request.getRequestIds().isEmpty()) {
            throw new BadRequestException("At least one request must be selected.");
        }
        List<Long> requestIds = request.getRequestIds().stream().distinct().toList();

        List<PurchaseRequestModel> requests = purchaseRequestRepository.findAllById(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new NotFoundException("One or more requests not found.");
        }
        for (PurchaseRequestModel pr : requests) {
            if (pr.getStatus() != PurchaseRequestStatus.APPROVED) {
                throw new BadRequestException(
                        "Only approved requests with sufficient warehouse stock can be dispatched. "
                                + "Requests awaiting stock must wait for supplier replenishment.");
            }
        }

        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());

        // Tổng SL cần xuất theo từng sản phẩm, quy đổi từ đơn vị TOP (thùng/kiện) sang đơn vị BASE
        // (đơn vị tồn kho tổng đang lưu trữ) qua ProductPackagingService.
        Map<Integer, Integer> neededByProduct = new HashMap<>();
        for (List<PurchaseRequestDetailModel> details : detailsByRequest.values()) {
            for (PurchaseRequestDetailModel detail : details) {
                int topUnitsQty = dispatchQuantity(detail);
                if (topUnitsQty > 0 && detail.getProductId() != null) {
                    int baseUnitsQty = productPackagingService.toBaseQty(topUnitsQty, productsById.get(detail.getProductId()));
                    neededByProduct.merge(detail.getProductId(), baseUnitsQty, Integer::sum);
                }
            }
        }

        // Kiểm tra & trừ tồn kho KHO TỔNG.
        Map<Integer, WarehouseInventoryModel> stockByProduct = warehouseInventoryRepository
                .findByProductIdIn(neededByProduct.keySet()).stream()
                .collect(Collectors.toMap(WarehouseInventoryModel::getProductId, inv -> inv, (a, b) -> a));

        List<String> shortages = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : neededByProduct.entrySet()) {
            WarehouseInventoryModel inv = stockByProduct.get(entry.getKey());
            int available = inv == null ? 0 : safe(inv.getQuantity());
            if (available < entry.getValue()) {
                shortages.add("product " + entry.getKey() + " (need " + entry.getValue() + ", have " + available + ")");
            }
        }
        if (!shortages.isEmpty()) {
            throw new BadRequestException("Insufficient warehouse stock for: " + String.join(", ", shortages));
        }
        for (Map.Entry<Integer, Integer> entry : neededByProduct.entrySet()) {
            WarehouseInventoryModel inv = stockByProduct.get(entry.getKey());
            inv.setQuantity(safe(inv.getQuantity()) - entry.getValue());
            warehouseInventoryRepository.save(inv);
        }

        Map<Long, BranchModel> branchesById = loadBranches(requests);
        DispatchOrderModel order = new DispatchOrderModel();
        order.setStatus(DispatchStatus.PREPARING);
        order.setVehicle(normalize(request.getVehicle()));
        order.setDeliveryArea(commonValue(requests, branchesById, BranchModel::getArea));
        order.setRoute(commonValue(requests, branchesById, BranchModel::getRoute));
        order.setCreatedBy(currentUserProvider.getCurrentUserOrThrow().getId());
        DispatchOrderModel savedOrder = dispatchOrderRepository.save(order);

        List<DispatchOrderRequestModel> links = new ArrayList<>();
        for (PurchaseRequestModel pr : requests) {
            DispatchOrderRequestModel link = new DispatchOrderRequestModel();
            link.setDispatchOrderId(savedOrder.getId());
            link.setPurchaseRequestId(pr.getId());
            links.add(link);
            pr.setStatus(PurchaseRequestStatus.DISPATCHING);
        }
        dispatchOrderRequestRepository.saveAll(links);
        purchaseRequestRepository.saveAll(requests);

        return buildDetail(savedOrder);
    }

    @Override
    public List<DispatchOrderResponse> getDispatchOrders() {
        List<DispatchOrderModel> orders = dispatchOrderRepository.findAllByOrderByCreatedAtDesc();
        return orders.stream().map(this::buildDetail).toList();
    }

    @Override
    public Page<DispatchOrderResponse> getDispatchOrderPage(
            PageRequestDTO pageRequest,
            DispatchStatus status
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<DispatchOrderModel> specification = (root, ignored, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        }
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            Long id = parseIdentifier(search);
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("vehicle")), pattern),
                    cb.like(cb.lower(root.get("deliveryArea")), pattern),
                    cb.like(cb.lower(root.get("route")), pattern),
                    id == null ? cb.disjunction() : cb.equal(root.get("id"), id)
            ));
        }
        Page<DispatchOrderModel> orderPage = dispatchOrderRepository.findAll(
                specification,
                query.toPageable(
                        "createdAt",
                        Sort.Direction.DESC,
                        Set.of("id", "status", "vehicle", "deliveryArea", "route", "createdAt", "deliveredAt")));
        return new PageImpl<>(
                buildDetails(orderPage.getContent()),
                orderPage.getPageable(),
                orderPage.getTotalElements());
    }

    private boolean matchesApprovedSearch(DispatchApprovedRequestResponse row, String search) {
        String normalized = search.toLowerCase(Locale.ROOT);
        return java.util.stream.Stream.of(
                        row.getRequestNumber(), row.getBranchName(), row.getArea(), row.getRoute())
                .filter(value -> value != null)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(normalized));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right.trim());
    }

    private Long parseIdentifier(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public DispatchOrderResponse getDispatchOrder(Long id) {
        DispatchOrderModel order = dispatchOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dispatch order not found."));
        return buildDetail(order);
    }

    @Override
    @Transactional
    public DispatchOrderResponse updateStatus(Long id, UpdateDispatchStatusRequest request) {
        DispatchOrderModel order = dispatchOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dispatch order not found."));

        DispatchStatus target;
        try {
            target = DispatchStatus.fromString(request == null ? null : request.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid dispatch status.");
        }
        if (target == null) {
            throw new BadRequestException("Status is required.");
        }
        if (target.isBranchReceiptOnly()) {
            throw new BadRequestException(
                    "Delivered status is set automatically when branch inventory staff confirms receipt.");
        }
        if (!target.isWarehouseSelectable()) {
            throw new BadRequestException("Invalid dispatch status for warehouse update.");
        }

        DispatchStatus current = order.getStatus() == null ? DispatchStatus.PREPARING : order.getStatus();
        if (current == DispatchStatus.RECEIVED) {
            throw new BadRequestException("Completed dispatch orders cannot be changed.");
        }
        if (target == current) {
            return buildDetail(order);
        }

        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(order.getId()).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        List<PurchaseRequestModel> purchaseRequests = purchaseRequestRepository.findAllById(requestIds);

        syncPurchaseRequestStatus(purchaseRequests, target);

        order.setStatus(target);
        return buildDetail(dispatchOrderRepository.save(order));
    }

    private void syncPurchaseRequestStatus(List<PurchaseRequestModel> purchaseRequests, DispatchStatus dispatchStatus) {
        PurchaseRequestStatus prStatus = switch (dispatchStatus) {
            case PREPARING, REDELIVERY -> PurchaseRequestStatus.DISPATCHING;
            case DELIVERING -> PurchaseRequestStatus.IN_TRANSIT;
            case RECEIVED -> PurchaseRequestStatus.RECEIVED;
        };
        for (PurchaseRequestModel pr : purchaseRequests) {
            if (pr.getStatus() == PurchaseRequestStatus.RECEIVED) {
                continue;
            }
            pr.setStatus(prStatus);
        }
        purchaseRequestRepository.saveAll(purchaseRequests);
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    private DispatchOrderResponse buildDetail(DispatchOrderModel order) {
        DispatchOrderResponse response = toBaseResponse(order);

        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(order.getId()).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        if (requestIds.isEmpty()) {
            return response;
        }

        List<PurchaseRequestModel> requests = purchaseRequestRepository.findAllById(requestIds);
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        appendRequests(
                response,
                requests,
                detailsByRequest,
                loadProducts(detailsByRequest.values()),
                loadBranches(requests));
        return response;
    }

    private List<DispatchOrderResponse> buildDetails(List<DispatchOrderModel> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(DispatchOrderModel::getId).toList();
        Map<Long, List<Long>> requestIdsByOrder = dispatchOrderRequestRepository
                .findByDispatchOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.groupingBy(
                        DispatchOrderRequestModel::getDispatchOrderId,
                        Collectors.mapping(DispatchOrderRequestModel::getPurchaseRequestId, Collectors.toList())));
        List<Long> requestIds = requestIdsByOrder.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, PurchaseRequestModel> requestsById = purchaseRequestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(PurchaseRequestModel::getId, Function.identity(), (left, right) -> left));
        List<PurchaseRequestModel> requests = new ArrayList<>(requestsById.values());
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        Map<Long, BranchModel> branchesById = loadBranches(requests);

        return orders.stream().map(order -> {
            DispatchOrderResponse response = toBaseResponse(order);
            List<PurchaseRequestModel> orderRequests = requestIdsByOrder
                    .getOrDefault(order.getId(), List.of())
                    .stream()
                    .map(requestsById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            appendRequests(response, orderRequests, detailsByRequest, productsById, branchesById);
            return response;
        }).toList();
    }

    private DispatchOrderResponse toBaseResponse(DispatchOrderModel order) {
        DispatchOrderResponse response = new DispatchOrderResponse();
        response.setId(order.getId());
        response.setDispatchNumber(dispatchMapper.toDispatchNumber(order));
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setVehicle(order.getVehicle());
        response.setDeliveryArea(order.getDeliveryArea());
        response.setRoute(order.getRoute());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());
        return response;
    }

    private void appendRequests(
            DispatchOrderResponse response,
            List<PurchaseRequestModel> requests,
            Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest,
            Map<Integer, ProductModel> productsById,
            Map<Long, BranchModel> branchesById
    ) {
        for (PurchaseRequestModel pr : requests) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
            BranchModel branch = branchesById.get(pr.getBranchId());

            DispatchOrderResponse.RequestLine line = new DispatchOrderResponse.RequestLine();
            line.setRequestId(pr.getId());
            line.setRequestNumber(dispatchMapper.toRequestNumber(pr));
            line.setBranchId(pr.getBranchId());
            line.setBranchName(branch == null ? null : branch.getName());
            line.setItemCount(details.size());

            List<DispatchOrderResponse.ItemLine> items = new ArrayList<>();
            for (PurchaseRequestDetailModel detail : details) {
                ProductModel product = productsById.get(detail.getProductId());
                DispatchOrderResponse.ItemLine item = new DispatchOrderResponse.ItemLine();
                item.setProductId(detail.getProductId());
                item.setProductCode(product == null ? null : product.getCode());
                item.setProductName(product == null ? null : product.getName());
                item.setUnit(product == null ? null : product.getUnit());
                item.setQuantity(dispatchQuantity(detail));
                item.setTopPackagingLabel(product == null ? null : productPackagingService.topLabel(product));
                items.add(item);
            }
            line.setItems(items);
            response.getRequests().add(line);
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

    private Map<Long, BranchModel> loadBranches(List<PurchaseRequestModel> requests) {
        Set<Long> branchIds = requests.stream()
                .map(PurchaseRequestModel::getBranchId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (branchIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(BranchModel::getId, b -> b, (a, b) -> a));
    }

    private List<String> distinctCategories(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, ProductModel> productsById
    ) {
        Set<String> categories = new LinkedHashSet<>();
        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            if (product != null && product.getCategory() != null && product.getCategory().getName() != null) {
                categories.add(product.getCategory().getName());
            }
        }
        return new ArrayList<>(categories);
    }

    private String commonValue(
            List<PurchaseRequestModel> requests,
            Map<Long, BranchModel> branchesById,
            java.util.function.Function<BranchModel, String> extractor
    ) {
        Set<String> values = new LinkedHashSet<>();
        for (PurchaseRequestModel pr : requests) {
            BranchModel branch = branchesById.get(pr.getBranchId());
            if (branch != null) {
                values.add(extractor.apply(branch));
            }
        }
        values.remove(null);
        if (values.isEmpty()) {
            return null;
        }
        return values.size() == 1 ? values.iterator().next() : "Mixed";
    }

    private int dispatchQuantity(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
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
}
