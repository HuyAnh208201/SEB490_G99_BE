package base.api.feature.inventorycount.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.dto.request.SubmitInventoryCountRequest;
import base.api.feature.inventorycount.dto.response.InventoryCountProductResponse;
import base.api.feature.inventorycount.dto.response.InventoryCountSessionResponse;
import base.api.feature.inventorycount.dto.response.InventoryCountSheetResponse;
import base.api.feature.inventorycount.repository.InventoryCountItemRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.inventorycount.service.IInventoryCountService;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.InventoryCountItemModel;
import base.api.shared.entity.InventoryCountSessionModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryCountServiceImpl implements IInventoryCountService {

    private static final String STATUS_PENDING = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private InventoryCountSessionRepository sessionRepository;

    @Autowired
    private InventoryCountItemRepository itemRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    public InventoryCountSheetResponse getCountSheet() {
        UserModel staff = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(staff);
        BranchModel branch = branchRepository.findById(branchId).orElse(null);

        Map<Integer, Integer> stockByProduct = branchInventoryRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(
                        BranchInventoryModel::getProductId,
                        inv -> safe(inv.getCurrentStock()),
                        (a, b) -> a));

        List<InventoryCountProductResponse> products = new ArrayList<>();
        for (ProductModel product : productRepository.findAllActiveProducts()) {
            InventoryCountProductResponse row = new InventoryCountProductResponse();
            row.setProductId(product.getId());
            row.setProductCode(product.getCode());
            row.setProductName(product.getName());
            row.setUnit(product.getUnit());
            row.setCategory(product.getCategory() == null ? null : product.getCategory().getName());
            row.setSystemQty(stockByProduct.getOrDefault(product.getId(), 0));
            products.add(row);
        }
        products.sort(Comparator.comparing(
                InventoryCountProductResponse::getProductCode,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        InventoryCountSheetResponse response = new InventoryCountSheetResponse();
        response.setSessionCode(nextSessionCode());
        response.setCountDate(LocalDate.now());
        response.setBranchId(branchId);
        response.setBranchName(branch == null ? null : branch.getName());
        response.setProducts(products);
        return response;
    }

    @Override
    @Transactional
    public InventoryCountSessionResponse submitCount(SubmitInventoryCountRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("At least one counted item is required.");
        }
        UserModel staff = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(staff);

        Map<Integer, Integer> stockByProduct = branchInventoryRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(
                        BranchInventoryModel::getProductId,
                        inv -> safe(inv.getCurrentStock()),
                        (a, b) -> a));
        Set<Integer> productIds = request.getItems().stream()
                .map(SubmitInventoryCountRequest.Item::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a));

        InventoryCountSessionModel session = new InventoryCountSessionModel();
        session.setBranchId(branchId);
        session.setCountDate(LocalDate.now());
        session.setCountedBy(staff.getId());
        session.setStatus(STATUS_PENDING);
        session.setNote(normalize(request.getNote()));
        session.setTotalProducts(productIds.size());
        InventoryCountSessionModel saved = sessionRepository.save(session);

        List<InventoryCountItemModel> items = new ArrayList<>();
        for (SubmitInventoryCountRequest.Item input : request.getItems()) {
            if (input.getProductId() == null || !productsById.containsKey(input.getProductId())) {
                throw new BadRequestException("Invalid product in count sheet.");
            }
            int systemQty = stockByProduct.getOrDefault(input.getProductId(), 0);
            int countedQty = safe(input.getCountedQty());

            InventoryCountItemModel item = new InventoryCountItemModel();
            item.setSessionId(saved.getId());
            item.setProductId(input.getProductId());
            item.setSystemQty(systemQty);
            item.setCountedQty(countedQty);
            item.setVariance(countedQty - systemQty);
            item.setNote(normalize(input.getNote()));
            items.add(item);
        }
        itemRepository.saveAll(items);

        return buildDetail(saved, items, productsById);
    }

    @Override
    public List<InventoryCountSessionResponse> getHistory() {
        Long branchId = requireBranch(currentUserProvider.getCurrentUserOrThrow());
        List<InventoryCountSessionModel> sessions = sessionRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNames = resolveUserNames(sessions);
        return sessions.stream().map(session -> buildSummary(session, userNames)).toList();
    }

    @Override
    public InventoryCountSessionResponse getSession(Long id) {
        Long branchId = requireBranch(currentUserProvider.getCurrentUserOrThrow());
        InventoryCountSessionModel session = loadBranchSession(id, branchId);
        List<InventoryCountItemModel> items = itemRepository.findBySessionId(id);
        Map<Integer, ProductModel> productsById = loadProducts(items);
        return buildDetail(session, items, productsById);
    }

    @Override
    @Transactional
    public InventoryCountSessionResponse approve(Long id) {
        UserModel reviewer = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(reviewer);
        InventoryCountSessionModel session = loadBranchSession(id, branchId);
        if (!STATUS_PENDING.equals(session.getStatus())) {
            throw new BadRequestException("Only pending sessions can be approved.");
        }

        List<InventoryCountItemModel> items = itemRepository.findBySessionId(id);
        for (InventoryCountItemModel item : items) {
            applyStock(branchId, item.getProductId(), safe(item.getCountedQty()));
        }

        session.setStatus(STATUS_APPROVED);
        session.setReviewedBy(reviewer.getId());
        session.setReviewedAt(LocalDateTime.now());
        sessionRepository.save(session);

        Map<Integer, ProductModel> productsById = loadProducts(items);
        return buildDetail(session, items, productsById);
    }

    @Override
    @Transactional
    public InventoryCountSessionResponse reject(Long id) {
        UserModel reviewer = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(reviewer);
        InventoryCountSessionModel session = loadBranchSession(id, branchId);
        if (!STATUS_PENDING.equals(session.getStatus())) {
            throw new BadRequestException("Only pending sessions can be rejected.");
        }
        session.setStatus(STATUS_REJECTED);
        session.setReviewedBy(reviewer.getId());
        session.setReviewedAt(LocalDateTime.now());
        sessionRepository.save(session);

        List<InventoryCountItemModel> items = itemRepository.findBySessionId(id);
        Map<Integer, ProductModel> productsById = loadProducts(items);
        return buildDetail(session, items, productsById);
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    private InventoryCountSessionResponse buildSummary(InventoryCountSessionModel session, Map<Long, String> userNames) {
        InventoryCountSessionResponse response = new InventoryCountSessionResponse();
        response.setId(session.getId());
        response.setSessionCode(sessionCode(session));
        response.setCountDate(session.getCountDate());
        response.setBranchId(session.getBranchId());
        response.setCountedByName(userNames.get(session.getCountedBy()));
        response.setReviewedByName(userNames.get(session.getReviewedBy()));
        response.setTotalProducts(session.getTotalProducts());
        response.setStatus(session.getStatus());
        response.setNote(session.getNote());
        response.setCreatedAt(session.getCreatedAt());
        response.setReviewedAt(session.getReviewedAt());
        return response;
    }

    private InventoryCountSessionResponse buildDetail(
            InventoryCountSessionModel session,
            List<InventoryCountItemModel> items,
            Map<Integer, ProductModel> productsById
    ) {
        Map<Long, String> userNames = resolveUserNames(List.of(session));
        InventoryCountSessionResponse response = buildSummary(session, userNames);
        BranchModel branch = branchRepository.findById(session.getBranchId()).orElse(null);
        response.setBranchName(branch == null ? null : branch.getName());

        for (InventoryCountItemModel item : items) {
            ProductModel product = productsById.get(item.getProductId());
            InventoryCountSessionResponse.Item line = new InventoryCountSessionResponse.Item();
            line.setProductId(item.getProductId());
            line.setProductCode(product == null ? null : product.getCode());
            line.setProductName(product == null ? null : product.getName());
            line.setUnit(product == null ? null : product.getUnit());
            line.setCategory(product == null || product.getCategory() == null ? null : product.getCategory().getName());
            line.setSystemQty(item.getSystemQty());
            line.setCountedQty(item.getCountedQty());
            line.setVariance(item.getVariance());
            line.setNote(item.getNote());
            response.getItems().add(line);
        }
        return response;
    }

    private Map<Integer, ProductModel> loadProducts(List<InventoryCountItemModel> items) {
        Set<Integer> productIds = items.stream()
                .map(InventoryCountItemModel::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, String> resolveUserNames(List<InventoryCountSessionModel> sessions) {
        Set<Long> ids = new LinkedHashSet<>();
        for (InventoryCountSessionModel session : sessions) {
            if (session.getCountedBy() != null) {
                ids.add(session.getCountedBy());
            }
            if (session.getReviewedBy() != null) {
                ids.add(session.getReviewedBy());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserModel::getId, UserModel::getFullName, (a, b) -> a));
    }

    private InventoryCountSessionModel loadBranchSession(Long id, Long branchId) {
        InventoryCountSessionModel session = sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory count session not found."));
        if (!branchId.equals(session.getBranchId())) {
            throw new ForbiddenException("Access denied.");
        }
        return session;
    }

    private void applyStock(Long branchId, Integer productId, int countedQty) {
        if (branchId == null || productId == null || countedQty < 0) {
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
        inventory.setCurrentStock(countedQty);
        branchInventoryRepository.save(inventory);
    }

    private String nextSessionCode() {
        return "CNT-" + LocalDate.now().format(SESSION_DATE) + "-XXX";
    }

    private String sessionCode(InventoryCountSessionModel session) {
        LocalDate date = session.getCountDate() == null ? LocalDate.now() : session.getCountDate();
        return "CNT-" + date.format(SESSION_DATE) + "-" + String.format("%03d", session.getId());
    }

    private Long requireBranch(UserModel user) {
        if (user.getBranchId() == null) {
            throw new ForbiddenException("Your account is not assigned to a branch.");
        }
        return user.getBranchId();
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
