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
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryCountServiceImpl implements IInventoryCountService {

    private static final String STATUS_COMPLETED = "COMPLETED";
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
        PageRequestDTO capped = new PageRequestDTO();
        capped.setPage(1);
        capped.setSize(PageRequestDTO.MAX_PAGE_SIZE);
        return getCountSheet(capped, null);
    }

    @Override
    public InventoryCountSheetResponse getCountSheet(PageRequestDTO pageRequest, Integer categoryId) {
        UserModel staff = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = requireBranch(staff);
        BranchModel branch = branchRepository.findById(branchId).orElse(null);
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;

        Map<Integer, Integer> stockByProduct = branchInventoryRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(
                        BranchInventoryModel::getProductId,
                        inv -> safe(inv.getCurrentStock()),
                        (a, b) -> a));

        Page<ProductModel> productPage = productRepository.findVisibleActiveProducts(
                false,
                branchId,
                categoryId,
                query.normalizedSearch(),
                query.toPageable("code", Sort.Direction.ASC, Set.of("id", "code", "name")));

        List<InventoryCountProductResponse> products = new ArrayList<>();
        for (ProductModel product : productPage.getContent()) {
            InventoryCountProductResponse row = new InventoryCountProductResponse();
            row.setProductId(product.getId());
            row.setProductCode(product.getCode());
            row.setProductName(product.getName());
            row.setUnit(product.getUnit());
            row.setCategory(product.getCategory() == null ? null : product.getCategory().getName());
            row.setSystemQty(stockByProduct.getOrDefault(product.getId(), 0));
            products.add(row);
        }

        InventoryCountSheetResponse response = new InventoryCountSheetResponse();
        response.setSessionCode(nextSessionCode());
        response.setCountDate(LocalDate.now());
        response.setBranchId(branchId);
        response.setBranchName(branch == null ? null : branch.getName());
        response.setProducts(products);
        response.setPage(productPage.getNumber() + 1);
        response.setTotalPages(productPage.getTotalPages());
        response.setTotalElements(productPage.getTotalElements());
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
        session.setStatus(STATUS_COMPLETED);
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

        Map<Integer, Integer> countedByProduct = new HashMap<>();
        for (InventoryCountItemModel item : items) {
            countedByProduct.put(item.getProductId(), safe(item.getCountedQty()));
        }
        applyStockBatch(branchId, countedByProduct);

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
        Map<Long, Integer> varianceCounts = loadVarianceCounts(sessions.stream().map(InventoryCountSessionModel::getId).toList());
        return sessions.stream()
                .map(session -> buildSummary(session, userNames, varianceCounts))
                .toList();
    }

    @Override
    public Page<InventoryCountSessionResponse> getHistoryPage(
            PageRequestDTO pageRequest,
            String status,
            String discrepancy,
            LocalDate from,
            LocalDate to) {
        Long branchId = requireBranch(currentUserProvider.getCurrentUserOrThrow());
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<InventoryCountSessionModel> specification = (root, ignored, cb) ->
                cb.equal(root.get("branchId"), branchId);
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase(Locale.ROOT)));
        }
        if (from != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.greaterThanOrEqualTo(root.get("countDate"), from));
        }
        if (to != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.lessThanOrEqualTo(root.get("countDate"), to));
        }
        specification = specification.and(discrepancySpecification(discrepancy));
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            Long requestedId = parseSessionId(search);
            Set<Long> matchingCounterIds = resolveCounterIdsByName(search);
            specification = specification.and((root, ignored, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (requestedId != null) {
                    predicates.add(cb.equal(root.get("id"), requestedId));
                }
                predicates.add(cb.like(cb.lower(root.get("note")), pattern));
                if (!matchingCounterIds.isEmpty()) {
                    predicates.add(root.get("countedBy").in(matchingCounterIds));
                }
                return cb.or(predicates.toArray(Predicate[]::new));
            });
        }
        Page<InventoryCountSessionModel> page = sessionRepository.findAll(
                specification,
                query.toPageable(
                        "createdAt",
                        Sort.Direction.DESC,
                        Set.of("id", "countDate", "status", "totalProducts", "createdAt", "reviewedAt")));
        Map<Long, String> userNames = resolveUserNames(page.getContent());
        Map<Long, Integer> varianceCounts = loadVarianceCounts(
                page.getContent().stream().map(InventoryCountSessionModel::getId).toList());
        List<InventoryCountSessionResponse> content = page.getContent().stream()
                .map(session -> buildSummary(session, userNames, varianceCounts))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private Specification<InventoryCountSessionModel> discrepancySpecification(String discrepancy) {
        if (discrepancy == null || discrepancy.isBlank() || "all".equalsIgnoreCase(discrepancy)) {
            return (root, ignored, cb) -> cb.conjunction();
        }
        boolean withVariance = "with".equalsIgnoreCase(discrepancy);
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<InventoryCountItemModel> itemRoot = subquery.from(InventoryCountItemModel.class);
            subquery.select(itemRoot.get("sessionId"))
                    .where(cb.and(
                            cb.equal(itemRoot.get("sessionId"), root.get("id")),
                            cb.notEqual(itemRoot.get("variance"), 0)));
            return withVariance ? cb.exists(subquery) : cb.not(cb.exists(subquery));
        };
    }

    private Set<Long> resolveCounterIdsByName(String search) {
        if (search == null || search.isBlank()) {
            return Set.of();
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return userRepository.findAll().stream()
                .filter(user -> user.getFullName() != null
                        && user.getFullName().toLowerCase(Locale.ROOT).contains(term))
                .map(UserModel::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, Integer> loadVarianceCounts(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return itemRepository.countVarianceBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue(),
                        (a, b) -> a));
    }

    private Long parseSessionId(String search) {
        String digits = search.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public InventoryCountSessionResponse getSession(Long id) {
        Long branchId = requireBranch(currentUserProvider.getCurrentUserOrThrow());
        InventoryCountSessionModel session = loadBranchSession(id, branchId);
        List<InventoryCountItemModel> items = itemRepository.findBySessionId(id);
        Map<Integer, ProductModel> productsById = loadProducts(items);
        return buildDetail(session, items, productsById);
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    private InventoryCountSessionResponse buildSummary(
            InventoryCountSessionModel session,
            Map<Long, String> userNames,
            Map<Long, Integer> varianceCounts) {
        InventoryCountSessionResponse response = new InventoryCountSessionResponse();
        response.setId(session.getId());
        response.setSessionCode(sessionCode(session));
        response.setCountDate(session.getCountDate());
        response.setBranchId(session.getBranchId());
        response.setCountedByName(userNames.get(session.getCountedBy()));
        response.setReviewedByName(userNames.get(session.getReviewedBy()));
        response.setTotalProducts(session.getTotalProducts());
        int varianceCount = varianceCounts.getOrDefault(session.getId(), 0);
        response.setVarianceCount(varianceCount);
        response.setHasDiscrepancy(varianceCount > 0);
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
        Map<Long, Integer> varianceCounts = loadVarianceCounts(List.of(session.getId()));
        InventoryCountSessionResponse response = buildSummary(session, userNames, varianceCounts);
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

    private void applyStockBatch(Long branchId, Map<Integer, Integer> countedByProductId) {
        if (branchId == null || countedByProductId == null || countedByProductId.isEmpty()) {
            return;
        }
        List<Integer> productIds = countedByProductId.keySet().stream()
                .filter(id -> id != null && countedByProductId.get(id) != null && countedByProductId.get(id) >= 0)
                .toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Integer, BranchInventoryModel> existing = branchInventoryRepository
                .findByBranchIdAndProductIdIn(branchId, productIds).stream()
                .collect(Collectors.toMap(BranchInventoryModel::getProductId, Function.identity(), (a, b) -> a));

        List<BranchInventoryModel> toSave = new ArrayList<>();
        for (Integer productId : productIds) {
            int countedQty = countedByProductId.get(productId);
            BranchInventoryModel inventory = existing.get(productId);
            if (inventory == null) {
                inventory = new BranchInventoryModel();
                inventory.setBranchId(branchId);
                inventory.setProductId(productId);
                inventory.setCurrentStock(0);
            }
            inventory.setCurrentStock(countedQty);
            toSave.add(inventory);
        }
        branchInventoryRepository.saveAll(toSave);
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
