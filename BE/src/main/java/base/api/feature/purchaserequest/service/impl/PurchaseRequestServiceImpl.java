package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.PurchaseRequestItemRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.service.IPurchaseRequestService;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PurchaseRequestServiceImpl implements IPurchaseRequestService {

    private static final Set<PurchaseRequestStatus> WAREHOUSE_VISIBLE_STATUSES = EnumSet.of(
            PurchaseRequestStatus.PENDING,
            PurchaseRequestStatus.PREPARING,
            PurchaseRequestStatus.SENT
    );

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PurchaseRequestMapper purchaseRequestMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Value("${purchase-request.default-reorder-point:10}")
    private Integer defaultReorderPoint;

    @Override
    @Transactional
    public PurchaseRequestResponse createDraft(CreatePurchaseRequestRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = resolveBranchManagerBranchId(currentUser);

        PurchaseRequestModel purchaseRequest = new PurchaseRequestModel();
        purchaseRequest.setBranchId(branchId);
        purchaseRequest.setReason(normalizeNullableText(request.getNotes()));
        purchaseRequest.setStatus(PurchaseRequestStatus.DRAFT);
        purchaseRequest.setCreatedBy(currentUser.getId());

        PurchaseRequestModel savedRequest = purchaseRequestRepository.save(purchaseRequest);
        replaceDetails(savedRequest.getId(), buildRequestedQuantities(request.getItems(), request.getAddAllRecommended(), branchId));
        return buildResponse(savedRequest);
    }

    @Override
    @Transactional
    public PurchaseRequestResponse saveDraft(Long id, SaveDraftRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        assertDraftEditable(purchaseRequest);

        purchaseRequest.setReason(normalizeNullableText(request.getNotes()));
        PurchaseRequestModel savedRequest = purchaseRequestRepository.save(purchaseRequest);
        replaceDetails(savedRequest.getId(), buildRequestedQuantities(request.getItems(), request.getAddAllRecommended(), savedRequest.getBranchId()));
        return buildResponse(savedRequest);
    }

    @Override
    @Transactional
    public PurchaseRequestResponse submitRequest(Long id, SubmitPurchaseRequestRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        assertDraftEditable(purchaseRequest);

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(id);
        if (details.isEmpty()) {
            throw new BadRequestException("Cannot submit empty request.");
        }
        if (details.stream().anyMatch(detail -> detail.getRequestedQty() == null || detail.getRequestedQty() <= 0)) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.PENDING);
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    @Override
    @Transactional
    public PurchaseRequestResponse cancelRequest(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);

        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isCancellable()) {
            throw new BadRequestException("Only draft requests can be cancelled.");
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.CANCELLED);
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    @Override
    public PurchaseRequestResponse getRequest(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        return buildResponse(purchaseRequest);
    }

    @Override
    public Page<PurchaseRequestSummaryResponse> getRequestHistory(PageRequestDTO pageRequest) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        Pageable pageable = newestFirst(pageRequest);

        Page<PurchaseRequestModel> requests;
        if (role == UserRole.ADMIN) {
            requests = purchaseRequestRepository.findAll(pageable);
        } else if (role == UserRole.WAREHOUSE_MANAGER) {
            requests = purchaseRequestRepository.findByStatusIn(WAREHOUSE_VISIBLE_STATUSES, pageable);
        } else if (role == UserRole.BRANCH_MANAGER) {
            Long branchId = resolveBranchManagerBranchId(currentUser);
            requests = purchaseRequestRepository.findByBranchId(branchId, pageable);
        } else {
            throw new ForbiddenException("Access denied.");
        }

        return requests.map(this::buildSummaryResponse);
    }

    @Override
    public List<RecommendedProductResponse> getRecommendedProducts() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = resolveBranchManagerBranchId(currentUser);
        return getRecommendedProductsForBranch(branchId);
    }

    @Override
    public Page<ProductSearchResponse> searchProducts(String keyword, PageRequestDTO pageRequest) {
        return productRepository.searchActiveProducts(normalizeNullableText(keyword), productSearchPage(pageRequest))
                .map(purchaseRequestMapper::toProductSearchResponse);
    }

    private PurchaseRequestModel findRequestOrThrow(Long id) {
        return purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Request not found."));
    }

    private Long resolveBranchManagerBranchId(UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.BRANCH_MANAGER || currentUser.getBranchId() == null) {
            throw new ForbiddenException("Access denied.");
        }
        return currentUser.getBranchId();
    }

    private void assertCanViewRequest(PurchaseRequestModel request, UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.ADMIN) {
            return;
        }
        if (role == UserRole.WAREHOUSE_MANAGER
                && request.getStatus() != null
                && request.getStatus().isWarehouseVisible()) {
            return;
        }
        if (role == UserRole.BRANCH_MANAGER
                && currentUser.getBranchId() != null
                && currentUser.getBranchId().equals(request.getBranchId())) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private void assertDraftEditable(PurchaseRequestModel request) {
        if (request.getStatus() == PurchaseRequestStatus.PENDING) {
            throw new ForbiddenException("Request already submitted.");
        }
        if (request.getStatus() == null || !request.getStatus().isEditable()) {
            throw new ForbiddenException("Cannot edit submitted request.");
        }
    }

    private Map<Integer, Integer> buildRequestedQuantities(
            List<PurchaseRequestItemRequest> items,
            Boolean addAllRecommended,
            Long branchId
    ) {
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        if (items != null) {
            for (PurchaseRequestItemRequest item : items) {
                if (item == null) {
                    continue;
                }
                Integer productId = item.getProductId();
                Integer requestedQty = toRequestedQuantity(item.getRequestedQty());
                addQuantity(quantities, productId, requestedQty);
            }
        }

        if (Boolean.TRUE.equals(addAllRecommended)) {
            for (RecommendedProductResponse recommendedProduct : getRecommendedProductsForBranch(branchId)) {
                if (recommendedProduct.getSuggestedQty() != null && recommendedProduct.getSuggestedQty() > 0) {
                    addQuantity(quantities, recommendedProduct.getProductId(), recommendedProduct.getSuggestedQty());
                }
            }
        }

        return quantities;
    }

    private void addQuantity(Map<Integer, Integer> quantities, Integer productId, Integer requestedQty) {
        if (productId == null) {
            throw new BadRequestException("Product is required.");
        }
        if (requestedQty == null || requestedQty <= 0) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
        quantities.merge(productId, requestedQty, Integer::sum);
    }

    private Integer toRequestedQuantity(BigDecimal requestedQty) {
        if (requestedQty == null) {
            throw new BadRequestException("Quantity is required.");
        }
        try {
            if (requestedQty.stripTrailingZeros().scale() > 0) {
                throw new ArithmeticException("decimal");
            }
            int value = requestedQty.intValueExact();
            if (value <= 0) {
                throw new BadRequestException("Quantity must be greater than zero.");
            }
            return value;
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Quantity must be an integer.");
        }
    }

    private void replaceDetails(Long requestId, Map<Integer, Integer> quantities) {
        detailRepository.deleteByPurchaseRequestId(requestId);
        if (quantities.isEmpty()) {
            return;
        }

        List<PurchaseRequestDetailModel> details = quantities.entrySet().stream()
                .map(entry -> {
                    ProductModel product = resolveActiveProduct(entry.getKey());
                    return purchaseRequestMapper.buildDetailSnapshot(requestId, product, entry.getValue());
                })
                .toList();
        detailRepository.saveAll(details);
    }

    private ProductModel resolveActiveProduct(Integer productId) {
        ProductModel product = productRepository.findByIdWithCategory(productId)
                .orElseThrow(() -> new NotFoundException("Product not found."));
        if (!"active".equalsIgnoreCase(product.getStatus())) {
            throw new NotFoundException("Product not found.");
        }
        return product;
    }

    private List<RecommendedProductResponse> getRecommendedProductsForBranch(Long branchId) {
        Map<Integer, Integer> currentStockByProductId = new HashMap<>();
        for (BranchInventoryModel inventory : branchInventoryRepository.findByBranchId(branchId)) {
            currentStockByProductId.put(inventory.getProductId(), safeStock(inventory.getCurrentStock()));
        }

        // TODO: load reorder point from branch_inventory / product master when that field exists.
        int reorderPoint = defaultReorderPoint == null ? 0 : defaultReorderPoint;

        return productRepository.findAllActiveProducts().stream()
                .map(product -> {
                    int currentStock = currentStockByProductId.getOrDefault(product.getId(), 0);
                    int suggestedQty = Math.max(reorderPoint - currentStock, 0);
                    return purchaseRequestMapper.toRecommendedProductResponse(product, currentStock, reorderPoint, suggestedQty);
                })
                .filter(product -> product.getCurrentStock() <= product.getReorderPoint())
                .toList();
    }

    private int safeStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private PurchaseRequestResponse buildResponse(PurchaseRequestModel request) {
        BranchModel branch = branchRepository.findById(request.getBranchId()).orElse(null);
        UserModel createdBy = userRepository.findById(request.getCreatedBy()).orElse(null);
        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(request.getId());
        Map<Integer, ProductModel> productsById = loadProductsById(details);
        return purchaseRequestMapper.toResponse(request, branch, createdBy, details, productsById);
    }

    private PurchaseRequestSummaryResponse buildSummaryResponse(PurchaseRequestModel request) {
        BranchModel branch = branchRepository.findById(request.getBranchId()).orElse(null);
        UserModel createdBy = userRepository.findById(request.getCreatedBy()).orElse(null);
        int itemCount = (int) detailRepository.countByPurchaseRequestId(request.getId());
        return purchaseRequestMapper.toSummaryResponse(request, itemCount, branch, createdBy);
    }

    private Pageable newestFirst(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return PageRequest.of(
                Math.max(0, safeRequest.getPage() - 1),
                Math.max(1, safeRequest.getSize()),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private Pageable productSearchPage(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return PageRequest.of(
                Math.max(0, safeRequest.getPage() - 1),
                Math.max(1, safeRequest.getSize()),
                Sort.by(Sort.Direction.ASC, "name")
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Map<Integer, ProductModel> loadProductsById(List<PurchaseRequestDetailModel> details) {
        Map<Integer, ProductModel> productsById = new HashMap<>();
        for (PurchaseRequestDetailModel detail : details) {
            if (detail.getProductId() == null || productsById.containsKey(detail.getProductId())) {
                continue;
            }
            productRepository.findByIdWithCategory(detail.getProductId())
                    .ifPresent(product -> productsById.put(product.getId(), product));
        }
        return productsById;
    }
}
