package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.security.CurrentUserProvider;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Warehouse Incoming Requests list: unfiltered WM history is PENDING + AWAITING_STOCK + APPROVED.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestHistoryTest {

    private static final Long USER_ID = 50L;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private PurchaseRequestDetailRepository detailRepository;

    @Mock
    private BranchInventoryRepository branchInventoryRepository;

    @Mock
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Mock
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;

    @Mock
    private GoodsReceiptItemRepository goodsReceiptItemRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private ProductPackagingService productPackagingService;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PurchaseRequestServiceImpl service;

    @Test
    void warehouseIncomingStatusesArePendingAwaitingAndApproved() {
        assertEquals(
                EnumSet.of(
                        PurchaseRequestStatus.PENDING,
                        PurchaseRequestStatus.AWAITING_STOCK,
                        PurchaseRequestStatus.APPROVED),
                PurchaseRequestServiceImpl.WAREHOUSE_INCOMING_STATUSES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void warehouseUnfilteredHistoryUsesIncomingStatusesNotPendingOnly() {
        signedInAsWarehouseManager();
        when(purchaseRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getRequestHistory(null, null, null);

        ArgumentCaptor<Specification<PurchaseRequestModel>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(purchaseRequestRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Root<PurchaseRequestModel> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> statusPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("status")).thenReturn((Path) statusPath);
        when(statusPath.in(anyCollection())).thenReturn(predicate);
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        specCaptor.getValue().toPredicate(root, query, cb);

        ArgumentCaptor<Collection<PurchaseRequestStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(statusPath, org.mockito.Mockito.atLeastOnce()).in(statusesCaptor.capture());

        boolean hasIncomingDefault = statusesCaptor.getAllValues().stream().anyMatch(values -> {
            Set<PurchaseRequestStatus> statuses = EnumSet.copyOf(List.copyOf(values));
            return statuses.equals(PurchaseRequestServiceImpl.WAREHOUSE_INCOMING_STATUSES);
        });
        assertTrue(hasIncomingDefault, "WM unfiltered list must include PENDING, AWAITING_STOCK, and APPROVED");
    }

    private void signedInAsWarehouseManager() {
        UserModel user = new UserModel();
        user.setId(USER_ID);
        user.setRole(UserRole.WAREHOUSE_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.WAREHOUSE_MANAGER);
    }
}
