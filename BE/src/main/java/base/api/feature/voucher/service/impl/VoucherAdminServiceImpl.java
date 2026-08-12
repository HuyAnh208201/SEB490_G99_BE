package base.api.feature.voucher.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.voucher.dto.request.IssueVoucherRequest;
import base.api.feature.voucher.dto.request.SaveVoucherCatalogRequest;
import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.feature.voucher.mapper.VoucherAdminMapper;
import base.api.feature.voucher.service.IVoucherAdminService;
import base.api.feature.voucher.service.VoucherCodeGenerator;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class VoucherAdminServiceImpl implements IVoucherAdminService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String STATUS_USED = "used";
    private static final String STATUS_REVOKED = "revoked";

    private static final String TYPE_PERCENT = "PERCENT";
    private static final String TYPE_FIXED = "FIXED";

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private static final Set<String> CATALOG_SORT_FIELDS =
            Set.of("id", "name", "discountType", "discountValue", "pointsRequired", "status");
    private static final Set<String> VOUCHER_SORT_FIELDS =
            Set.of("id", "code", "status", "expiresAt", "createdAt");

    @Autowired
    private VoucherCodeGenerator voucherCodeGenerator;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private OrderDiscountRepository orderDiscountRepository;

    @Autowired
    private VoucherCatalogRepository voucherCatalogRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private VoucherAdminMapper voucherAdminMapper;

    // =========================================================================
    // Voucher types
    // =========================================================================

    @Override
    public List<VoucherCatalogResponse> getAllCatalogs() {
        return voucherCatalogRepository.findAllByOrderByIdAsc().stream()
                .map(catalog -> voucherAdminMapper.toResponse(catalog, isCatalogInUse(catalog.getId())))
                .toList();
    }

    @Override
    public Page<VoucherCatalogResponse> getCatalogPage(PageRequestDTO pageRequest) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        String search = query.normalizedSearch();
        var pageable = query.toPageable("id", Sort.Direction.ASC, CATALOG_SORT_FIELDS);
        Page<VoucherCatalogModel> page = search == null
                ? voucherCatalogRepository.findAll(pageable)
                : voucherCatalogRepository.findByNameContainingIgnoreCase(search, pageable);
        return page.map(catalog -> voucherAdminMapper.toResponse(catalog, isCatalogInUse(catalog.getId())));
    }

    @Override
    public VoucherCatalogResponse getCatalog(Long id) {
        VoucherCatalogModel catalog = findCatalogOrThrow(id);
        return voucherAdminMapper.toResponse(catalog, isCatalogInUse(id));
    }

    @Override
    @Transactional
    public VoucherCatalogResponse createCatalog(SaveVoucherCatalogRequest request) {
        String name = normalizeRequiredText(request.getName(), "Discount type name is required.");
        if (voucherCatalogRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Discount type already exists.");
        }

        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setName(name);
        applyCatalogFields(catalog, request);
        catalog.setStatus(parseCatalogStatus(request.getStatus(), STATUS_ACTIVE));
        return voucherAdminMapper.toResponse(voucherCatalogRepository.save(catalog), false);
    }

    @Override
    @Transactional
    public VoucherCatalogResponse updateCatalog(Long id, SaveVoucherCatalogRequest request) {
        VoucherCatalogModel catalog = findCatalogOrThrow(id);
        String name = normalizeRequiredText(request.getName(), "Discount type name is required.");
        if (voucherCatalogRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Discount type already exists.");
        }

        // Customers already hold codes of this type, some bought with points, so changing the
        // discount changes what they paid for. Name, point price and status stay editable.
        boolean inUse = isCatalogInUse(id);
        if (inUse && discountChanged(catalog, request)) {
            throw new ConflictException(
                    "This discount type has issued codes. Create a new type instead of changing its value.");
        }

        catalog.setName(name);
        applyCatalogFields(catalog, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            catalog.setStatus(parseCatalogStatus(request.getStatus(), STATUS_ACTIVE));
        }
        return voucherAdminMapper.toResponse(voucherCatalogRepository.save(catalog), inUse);
    }

    @Override
    @Transactional
    public VoucherCatalogResponse setCatalogStatus(Long id, String status) {
        VoucherCatalogModel catalog = findCatalogOrThrow(id);
        catalog.setStatus(parseCatalogStatus(status, null));
        return voucherAdminMapper.toResponse(voucherCatalogRepository.save(catalog), isCatalogInUse(id));
    }

    @Override
    @Transactional
    public void deleteCatalog(Long id) {
        VoucherCatalogModel catalog = findCatalogOrThrow(id);
        // Deleting a type that still has codes leaves them orphaned, and the counter then
        // refuses them with a confusing message. Use inactive so old codes remain traceable.
        if (isCatalogInUse(id)) {
            throw new ConflictException(
                    "This discount type has issued codes. Set it to inactive instead of deleting.");
        }
        voucherCatalogRepository.delete(catalog);
    }

    // =========================================================================
    // Issued codes
    // =========================================================================

    @Override
    public Page<VoucherAdminResponse> getVoucherPage(
            PageRequestDTO pageRequest, String status, Long voucherCatalogId, Long customerId) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<VoucherModel> specification = (root, ignored, cb) -> cb.conjunction();

        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) ->
                    cb.like(cb.lower(root.get("code")), pattern));
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.lower(root.get("status")), normalized));
        }
        if (voucherCatalogId != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("voucherCatalogId"), voucherCatalogId));
        }
        if (customerId != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("customerId"), customerId));
        }

        Page<VoucherModel> vouchers = voucherRepository.findAll(
                specification,
                query.toPageable("id", Sort.Direction.DESC, VOUCHER_SORT_FIELDS));
        return hydrate(vouchers);
    }

    @Override
    @Transactional
    public List<VoucherAdminResponse> issueVouchers(IssueVoucherRequest request) {
        VoucherCatalogModel catalog = findCatalogOrThrow(request.getVoucherCatalogId());
        if (!STATUS_ACTIVE.equalsIgnoreCase(catalog.getStatus())) {
            throw new BadRequestException("Cannot issue codes for an inactive discount type.");
        }

        LocalDateTime expiresAt = request.getExpiresAt();
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Expiry date must be in the future.");
        }

        UserModel customer = resolveCustomer(request.getCustomerId());
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        // Batch-generating codes reserved for one person would make every code belong to that
        // person — pointless, and easily mistaken for shared codes.
        if (customer != null && quantity != 1) {
            throw new BadRequestException("A customer-specific code can only be issued one at a time.");
        }

        String prefix = voucherCodeGenerator.normalizePrefix(request.getCodePrefix());
        List<VoucherModel> issued = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            VoucherModel voucher = new VoucherModel();
            voucher.setCode(voucherCodeGenerator.generate(prefix));
            voucher.setVoucherCatalogId(catalog.getId());
            voucher.setCustomerId(customer == null ? null : customer.getId());
            voucher.setStatus(STATUS_ACTIVE);
            voucher.setExpiresAt(expiresAt);
            voucher.setCreatedAt(LocalDateTime.now());
            issued.add(voucherRepository.save(voucher));
        }

        return issued.stream()
                .map(voucher -> voucherAdminMapper.toResponse(voucher, catalog, customer))
                .toList();
    }

    @Override
    @Transactional
    public VoucherAdminResponse revokeVoucher(Long id) {
        VoucherModel voucher = findVoucherOrThrow(id);
        if (STATUS_USED.equalsIgnoreCase(voucher.getStatus())) {
            throw new BadRequestException("This code has already been used.");
        }
        if (STATUS_REVOKED.equalsIgnoreCase(voucher.getStatus())) {
            throw new BadRequestException("This code has already been revoked.");
        }
        // A status of its own rather than a fake past expiry. markActive only flips 'used' to
        // 'active', so a refund cannot resurrect a code an admin revoked.
        voucher.setStatus(STATUS_REVOKED);
        VoucherModel saved = voucherRepository.save(voucher);
        return voucherAdminMapper.toResponse(saved, catalogOrNull(saved), customerOrNull(saved));
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        VoucherModel voucher = findVoucherOrThrow(id);
        // Status is not consulted: a refund returns a code to 'active' while order_discounts
        // still points at it, so deleting then would orphan a reference on an old invoice.
        if (STATUS_USED.equalsIgnoreCase(voucher.getStatus())
                || orderDiscountRepository.existsByVoucherId(id)) {
            throw new ConflictException(
                    "This code has been used on an order and cannot be deleted.");
        }
        voucherRepository.delete(voucher);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void applyCatalogFields(VoucherCatalogModel catalog, SaveVoucherCatalogRequest request) {
        String discountType = parseDiscountType(request.getDiscountType());
        BigDecimal value = request.getDiscountValue();
        if (TYPE_PERCENT.equals(discountType)) {
            if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(ONE_HUNDRED) > 0) {
                throw new BadRequestException("Percent discount must be between 0 and 100.");
            }
        } else if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Fixed discount must be greater than 0.");
        }

        catalog.setDiscountType(discountType);
        catalog.setDiscountValue(value);
        // Empty means "leave as is", not "remove from point redemption": silently resetting to
        // 0 would quietly switch that flow off for this type. On create the default is 0.
        if (request.getPointsRequired() != null) {
            catalog.setPointsRequired(request.getPointsRequired());
        } else if (catalog.getPointsRequired() == null) {
            catalog.setPointsRequired(0);
        }
    }

    /** Compared with compareTo: 10000 and 10000.00 are the same discount. */
    private boolean discountChanged(VoucherCatalogModel catalog, SaveVoucherCatalogRequest request) {
        if (!parseDiscountType(request.getDiscountType()).equalsIgnoreCase(catalog.getDiscountType())) {
            return true;
        }
        BigDecimal current = catalog.getDiscountValue();
        BigDecimal requested = request.getDiscountValue();
        if (current == null || requested == null) {
            return current != requested;
        }
        return current.compareTo(requested) != 0;
    }

    private String parseDiscountType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (TYPE_PERCENT.equals(normalized) || TYPE_FIXED.equals(normalized)) {
            return normalized;
        }
        throw new BadRequestException("Discount type must be PERCENT or FIXED.");
    }

    private String parseCatalogStatus(String value, String fallback) {
        if (value == null || value.isBlank()) {
            if (fallback == null) {
                throw new BadRequestException("Status must be active or inactive.");
            }
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (STATUS_ACTIVE.equals(normalized) || STATUS_INACTIVE.equals(normalized)) {
            return normalized;
        }
        throw new BadRequestException("Status must be active or inactive.");
    }

    /**
     * customer_id points at users.id, not customers.id — mixing the two id spaces is exactly
     * the bug that matches a personal code to the wrong person. Only a CUSTOMER user is accepted.
     */
    private UserModel resolveCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        UserModel customer = userRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found."));
        if (customer.getRole() == null || customer.getRole().toWebRole() != UserRole.CUSTOMER) {
            throw new BadRequestException("Codes can only be issued to customer accounts.");
        }
        return customer;
    }

    private boolean isCatalogInUse(Long catalogId) {
        return voucherRepository.existsByVoucherCatalogId(catalogId);
    }

    private VoucherCatalogModel findCatalogOrThrow(Long id) {
        return voucherCatalogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Discount type not found."));
    }

    private VoucherModel findVoucherOrThrow(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Discount code not found."));
    }

    private VoucherCatalogModel catalogOrNull(VoucherModel voucher) {
        return voucher.getVoucherCatalogId() == null
                ? null
                : voucherCatalogRepository.findById(voucher.getVoucherCatalogId()).orElse(null);
    }

    private UserModel customerOrNull(VoucherModel voucher) {
        return voucher.getCustomerId() == null
                ? null
                : userRepository.findById(voucher.getCustomerId()).orElse(null);
    }

    /** Loads types and customers in bulk so the list does not fire N+1 queries. */
    private Page<VoucherAdminResponse> hydrate(Page<VoucherModel> vouchers) {
        List<Long> catalogIds = vouchers.getContent().stream()
                .map(VoucherModel::getVoucherCatalogId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<Long> customerIds = vouchers.getContent().stream()
                .map(VoucherModel::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, VoucherCatalogModel> catalogs = new LinkedHashMap<>();
        if (!catalogIds.isEmpty()) {
            voucherCatalogRepository.findAllById(catalogIds)
                    .forEach(catalog -> catalogs.put(catalog.getId(), catalog));
        }
        Map<Long, UserModel> customers = new LinkedHashMap<>();
        if (!customerIds.isEmpty()) {
            userRepository.findAllById(customerIds)
                    .forEach(customer -> customers.put(customer.getId(), customer));
        }

        return vouchers.map(voucher -> voucherAdminMapper.toResponse(
                voucher,
                catalogs.get(voucher.getVoucherCatalogId()),
                customers.get(voucher.getCustomerId())));
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = value == null ? null : value.trim().replaceAll("\\s+", " ");
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        if (normalized.length() > 255) {
            throw new BadRequestException("Discount type name must not exceed 255 characters.");
        }
        return normalized;
    }
}
