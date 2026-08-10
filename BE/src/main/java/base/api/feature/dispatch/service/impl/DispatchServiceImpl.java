package base.api.feature.dispatch.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchApprovedRequestResponse;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.dispatch.repository.DispatchOrderSupplierRepository;
import base.api.feature.dispatch.service.IDispatchService;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.DispatchOrderRequestModel;
import base.api.shared.entity.DispatchOrderSupplierModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.SupplierModel;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private DispatchOrderSupplierRepository dispatchOrderSupplierRepository;

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
    private ISupplierRepository supplierRepository;

    @Autowired
    private DispatchMapper dispatchMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ProductPackagingService productPackagingService;

    @Override
    public List<DispatchApprovedRequestResponse> getApprovedRequests() {
        List<PurchaseRequestModel> requests = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        return buildApprovedResponses(requests);
    }

    @Override
    public Page<DispatchApprovedRequestResponse> getApprovedRequestPage(
            PageRequestDTO pageRequest,
            String area,
            String route
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        String search = query.normalizedSearch();
        Specification<PurchaseRequestModel> specification = approvedRequestSpecification(search, area, route);
        Page<PurchaseRequestModel> requestPage = purchaseRequestRepository.findAll(
                specification,
                query.toPageable("createdAt", Sort.Direction.ASC, Set.of("id", "createdAt", "branchId")));
        List<DispatchApprovedRequestResponse> responses = buildApprovedResponses(requestPage.getContent());
        return new PageImpl<>(responses, requestPage.getPageable(), requestPage.getTotalElements());
    }

    private Specification<PurchaseRequestModel> approvedRequestSpecification(String search, String area, String route) {
        Specification<PurchaseRequestModel> specification = (root, ignored, cb) ->
                cb.equal(root.get("status"), PurchaseRequestStatus.APPROVED);

        if (area != null && !area.isBlank()) {
            String normalizedArea = area.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, cb) -> {
                var branchSubquery = query.subquery(Long.class);
                var branch = branchSubquery.from(BranchModel.class);
                branchSubquery.select(branch.get("id"))
                        .where(
                                cb.equal(branch.get("id"), root.get("branchId")),
                                cb.equal(cb.lower(branch.get("area")), normalizedArea));
                return root.get("branchId").in(branchSubquery);
            });
        }

        if (route != null && !route.isBlank()) {
            String normalizedRoute = route.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, cb) -> {
                var branchSubquery = query.subquery(Long.class);
                var branch = branchSubquery.from(BranchModel.class);
                branchSubquery.select(branch.get("id"))
                        .where(
                                cb.equal(branch.get("id"), root.get("branchId")),
                                cb.equal(cb.lower(branch.get("route")), normalizedRoute));
                return root.get("branchId").in(branchSubquery);
            });
        }

        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            Long searchedId = parseIdentifier(search);
            specification = specification.and((root, query, cb) -> {
                var branchSubquery = query.subquery(Long.class);
                var branch = branchSubquery.from(BranchModel.class);
                branchSubquery.select(branch.get("id"))
                        .where(cb.or(
                                cb.like(cb.lower(branch.get("name")), pattern),
                                cb.like(cb.lower(branch.get("area")), pattern),
                                cb.like(cb.lower(branch.get("route")), pattern)));

                var branchMatch = root.get("branchId").in(branchSubquery);
                return searchedId == null
                        ? branchMatch
                        : cb.or(branchMatch, cb.equal(root.get("id"), searchedId));
            });
        }

        return specification;
    }

    private List<DispatchApprovedRequestResponse> buildApprovedResponses(List<PurchaseRequestModel> requests) {
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
            List<String> categories = distinctCategories(details, productsById);
            List<String> shortDateCategories = distinctShortDateCategories(details, productsById);
            response.setCategories(categories);
            response.setShortDateCategories(shortDateCategories);
            response.setHasShortDateCategories(!shortDateCategories.isEmpty());
            response.setCreatedAt(request.getCreatedAt());
            result.add(response);
        }
        return result;
    }

    @Override
    @Transactional
    public DispatchOrderResponse createDispatchOrder(CreateDispatchOrderRequest request) {
        if (request == null || request.getRequestId() == null) {
            throw new BadRequestException("Request id is required.");
        }
        Long requestId = request.getRequestId();

        PurchaseRequestModel purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found."));
        if (purchaseRequest.getStatus() != PurchaseRequestStatus.APPROVED) {
            throw new BadRequestException(
                    "Only approved requests with sufficient warehouse stock can be dispatched. "
                            + "Requests awaiting stock must wait for supplier replenishment.");
        }

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(requestId);
        Map<Integer, ProductModel> productsById = loadProducts(List.of(details));
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);

        boolean hasShortDate = details.stream()
                .map(d -> productsById.get(d.getProductId()))
                .anyMatch(this::isShortDateProduct);
        List<Integer> supplierIds = normalizeSupplierIds(request.getSupplierIds());
        if (hasShortDate && supplierIds.isEmpty()) {
            throw new BadRequestException(
                    "Select at least one supplier for short-date (perishable) categories.");
        }
        if (!supplierIds.isEmpty()) {
            validateSuppliersExist(supplierIds);
        }

        Map<Integer, Integer> neededByProduct = new HashMap<>();
        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            if (isShortDateProduct(product)) {
                continue; // supplier-direct — no central warehouse deduction
            }
            int topUnitsQty = dispatchQuantity(detail);
            if (topUnitsQty > 0 && detail.getProductId() != null) {
                ProductPackagingModel top = topPackagings.get(detail.getProductId());
                int baseUnitsQty = top != null
                        ? productPackagingService.toBaseQty(topUnitsQty, top)
                        : productPackagingService.toBaseQty(topUnitsQty, product);
                neededByProduct.merge(detail.getProductId(), baseUnitsQty, Integer::sum);
            }
        }

        if (!neededByProduct.isEmpty()) {
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
            List<WarehouseInventoryModel> stockUpdates = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : neededByProduct.entrySet()) {
                WarehouseInventoryModel inv = stockByProduct.get(entry.getKey());
                inv.setQuantity(safe(inv.getQuantity()) - entry.getValue());
                stockUpdates.add(inv);
            }
            warehouseInventoryRepository.saveAll(stockUpdates);
        }

        BranchModel branch = purchaseRequest.getBranchId() == null
                ? null
                : branchRepository.findById(purchaseRequest.getBranchId()).orElse(null);

        DispatchOrderModel order = new DispatchOrderModel();
        order.setStatus(DispatchStatus.PREPARING);
        order.setDeliveryArea(branch == null ? null : branch.getArea());
        order.setRoute(branch == null ? null : branch.getRoute());
        order.setCreatedBy(currentUserProvider.getCurrentUserOrThrow().getId());
        DispatchOrderModel savedOrder = dispatchOrderRepository.save(order);

        DispatchOrderRequestModel link = new DispatchOrderRequestModel();
        link.setDispatchOrderId(savedOrder.getId());
        link.setPurchaseRequestId(purchaseRequest.getId());
        dispatchOrderRequestRepository.save(link);

        if (!supplierIds.isEmpty()) {
            List<DispatchOrderSupplierModel> supplierLinks = new ArrayList<>();
            for (Integer supplierId : supplierIds) {
                DispatchOrderSupplierModel row = new DispatchOrderSupplierModel();
                row.setDispatchOrderId(savedOrder.getId());
                row.setSupplierId(supplierId);
                supplierLinks.add(row);
            }
            dispatchOrderSupplierRepository.saveAll(supplierLinks);
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.DISPATCHING);
        purchaseRequestRepository.save(purchaseRequest);

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
                        Set.of("id", "status", "deliveryArea", "route", "createdAt", "deliveredAt")));
        return new PageImpl<>(
                buildDetails(orderPage.getContent()),
                orderPage.getPageable(),
                orderPage.getTotalElements());
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
        attachSuppliers(response, List.of(order.getId()));

        List<Long> requestIds = dispatchOrderRequestRepository.findByDispatchOrderId(order.getId()).stream()
                .map(DispatchOrderRequestModel::getPurchaseRequestId)
                .toList();
        if (requestIds.isEmpty()) {
            return response;
        }

        List<PurchaseRequestModel> requests = purchaseRequestRepository.findAllById(requestIds);
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = loadDetailsByRequest(requestIds);
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);
        appendRequests(
                response,
                requests,
                detailsByRequest,
                productsById,
                loadBranches(requests),
                topPackagings);
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
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);
        Map<Long, BranchModel> branchesById = loadBranches(requests);
        Map<Long, List<DispatchOrderSupplierModel>> suppliersByOrder = dispatchOrderSupplierRepository
                .findByDispatchOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(DispatchOrderSupplierModel::getDispatchOrderId));
        Map<Integer, SupplierModel> suppliersById = loadSuppliers(
                suppliersByOrder.values().stream()
                        .flatMap(List::stream)
                        .map(DispatchOrderSupplierModel::getSupplierId)
                        .collect(Collectors.toSet()));

        return orders.stream().map(order -> {
            DispatchOrderResponse response = toBaseResponse(order);
            applySupplierInfo(response, suppliersByOrder.getOrDefault(order.getId(), List.of()), suppliersById);
            List<PurchaseRequestModel> orderRequests = requestIdsByOrder
                    .getOrDefault(order.getId(), List.of())
                    .stream()
                    .map(requestsById::get)
                    .filter(Objects::nonNull)
                    .toList();
            appendRequests(response, orderRequests, detailsByRequest, productsById, branchesById, topPackagings);
            return response;
        }).toList();
    }

    private void attachSuppliers(DispatchOrderResponse response, List<Long> orderIds) {
        if (orderIds.isEmpty() || response.getId() == null) {
            return;
        }
        List<DispatchOrderSupplierModel> links = dispatchOrderSupplierRepository.findByDispatchOrderId(response.getId());
        Map<Integer, SupplierModel> suppliersById = loadSuppliers(
                links.stream().map(DispatchOrderSupplierModel::getSupplierId).collect(Collectors.toSet()));
        applySupplierInfo(response, links, suppliersById);
    }

    private void applySupplierInfo(
            DispatchOrderResponse response,
            List<DispatchOrderSupplierModel> links,
            Map<Integer, SupplierModel> suppliersById
    ) {
        List<Integer> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (DispatchOrderSupplierModel link : links) {
            ids.add(link.getSupplierId());
            SupplierModel supplier = suppliersById.get(link.getSupplierId());
            names.add(supplier == null ? ("Supplier #" + link.getSupplierId()) : supplier.getName());
        }
        response.setSupplierIds(ids);
        response.setSupplierNames(names);
    }

    private Map<Integer, SupplierModel> loadSuppliers(Set<Integer> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(SupplierModel::getId, Function.identity(), (a, b) -> a));
    }

    private DispatchOrderResponse toBaseResponse(DispatchOrderModel order) {
        DispatchOrderResponse response = new DispatchOrderResponse();
        response.setId(order.getId());
        response.setDispatchNumber(dispatchMapper.toDispatchNumber(order));
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
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
            Map<Long, BranchModel> branchesById,
            Map<Integer, ProductPackagingModel> topPackagings
    ) {
        Map<Integer, ProductPackagingModel> packagings = topPackagings == null ? Map.of() : topPackagings;
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
                ProductPackagingModel top = packagings.get(detail.getProductId());
                DispatchOrderResponse.ItemLine item = new DispatchOrderResponse.ItemLine();
                item.setProductId(detail.getProductId());
                item.setProductCode(product == null ? null : product.getCode());
                item.setProductName(product == null ? null : product.getName());
                item.setUnit(product == null ? null : product.getUnit());
                item.setQuantity(dispatchQuantity(detail));
                item.setTopPackagingLabel(top == null ? null : top.displayLabel());
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

    private Map<Integer, ProductPackagingModel> loadTopPackagings(Map<Integer, ProductModel> productsById) {
        if (productsById == null || productsById.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProductPackagingModel> topPackagings = new HashMap<>(
                productPackagingService.getTopPackagingsByProductIds(productsById.keySet()));
        for (ProductModel product : productsById.values()) {
            if (!topPackagings.containsKey(product.getId())) {
                ProductPackagingModel top = productPackagingService.getTopPackaging(product);
                if (top != null) {
                    topPackagings.put(product.getId(), top);
                }
            }
        }
        return topPackagings;
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

    private List<String> distinctShortDateCategories(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, ProductModel> productsById
    ) {
        Set<String> categories = new LinkedHashSet<>();
        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            if (isShortDateProduct(product) && product.getCategory().getName() != null) {
                categories.add(product.getCategory().getName());
            }
        }
        return new ArrayList<>(categories);
    }

    private boolean isShortDateProduct(ProductModel product) {
        return product != null
                && product.getCategory() != null
                && Boolean.TRUE.equals(product.getCategory().getShortDate());
    }

    private List<Integer> normalizeSupplierIds(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void validateSuppliersExist(List<Integer> supplierIds) {
        List<SupplierModel> found = supplierRepository.findAllById(supplierIds);
        if (found.size() != supplierIds.size()) {
            throw new BadRequestException("One or more selected suppliers were not found.");
        }
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
}
