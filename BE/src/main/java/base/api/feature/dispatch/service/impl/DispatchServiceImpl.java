package base.api.feature.dispatch.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchApprovedRequestResponse;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.dispatch.service.IDispatchService;
import base.api.shared.entity.BranchInventoryModel;
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
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private DispatchMapper dispatchMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    public List<DispatchApprovedRequestResponse> getApprovedRequests() {
        List<PurchaseRequestModel> requests = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
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
            if (pr.getStatus() == null || !pr.getStatus().isDispatchable()) {
                throw new BadRequestException("Only approved requests can be dispatched.");
            }
        }

        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);

        // Tổng SL cần xuất theo từng sản phẩm.
        Map<Integer, Integer> neededByProduct = new HashMap<>();
        for (List<PurchaseRequestDetailModel> details : detailsByRequest.values()) {
            for (PurchaseRequestDetailModel detail : details) {
                int qty = dispatchQuantity(detail);
                if (qty > 0 && detail.getProductId() != null) {
                    neededByProduct.merge(detail.getProductId(), qty, Integer::sum);
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

        DispatchStatus current = order.getStatus() == null ? DispatchStatus.PREPARING : order.getStatus();
        if (target.ordinal() < current.ordinal()) {
            throw new BadRequestException("Cannot move dispatch status backwards.");
        }
        if (target == current) {
            return buildDetail(order);
        }

        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(order.getId()).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        List<PurchaseRequestModel> requests = purchaseRequestRepository.findAllById(requestIds);

        if (target == DispatchStatus.DELIVERING) {
            requests.forEach(pr -> pr.setStatus(PurchaseRequestStatus.IN_TRANSIT));
            purchaseRequestRepository.saveAll(requests);
        } else if (target == DispatchStatus.RECEIVED) {
            Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
            for (PurchaseRequestModel pr : requests) {
                for (PurchaseRequestDetailModel detail : detailsByRequest.getOrDefault(pr.getId(), List.of())) {
                    increaseBranchStock(pr.getBranchId(), detail.getProductId(), dispatchQuantity(detail));
                }
                pr.setStatus(PurchaseRequestStatus.RECEIVED);
            }
            purchaseRequestRepository.saveAll(requests);
            order.setDeliveredAt(LocalDateTime.now());
        }

        order.setStatus(target);
        return buildDetail(dispatchOrderRepository.save(order));
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    private DispatchOrderResponse buildDetail(DispatchOrderModel order) {
        DispatchOrderResponse response = new DispatchOrderResponse();
        response.setId(order.getId());
        response.setDispatchNumber(dispatchMapper.toDispatchNumber(order));
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setVehicle(order.getVehicle());
        response.setDeliveryArea(order.getDeliveryArea());
        response.setRoute(order.getRoute());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(order.getId()).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        if (requestIds.isEmpty()) {
            return response;
        }

        List<PurchaseRequestModel> requests = purchaseRequestRepository.findAllById(requestIds);
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        Map<Long, BranchModel> branchesById = loadBranches(requests);

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
                items.add(item);
            }
            line.setItems(items);
            response.getRequests().add(line);
        }
        return response;
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
}
