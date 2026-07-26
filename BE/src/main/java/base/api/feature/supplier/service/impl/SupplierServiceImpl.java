package base.api.feature.supplier.service.impl;

import base.api.feature.supplier.dto.request.CreateSupplierRequest;
import base.api.feature.supplier.dto.request.UpdateSupplierRequest;
import base.api.feature.supplier.dto.response.SupplierResponse;
import base.api.feature.supplier.mapper.SupplierMapper;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.feature.supplier.service.ISupplierService;
import base.api.shared.entity.SupplierModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SupplierServiceImpl implements ISupplierService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    @Autowired
    private ISupplierRepository supplierRepository;

    @Autowired
    private SupplierMapper supplierMapper;

    @Override
    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        String normalizedName = normalizeRequiredText(request.getName(), "Supplier name is required.");
        String normalizedContactPerson = normalizeNullableText(request.getContactPerson());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedAddress = normalizeNullableText(request.getAddress());

        validateDuplicateName(normalizedName, null);

        SupplierModel supplier = new SupplierModel();
        supplier.setName(normalizedName);
        supplier.setContactPerson(normalizedContactPerson);
        supplier.setPhone(normalizedPhone);
        supplier.setAddress(normalizedAddress);
        supplier.setStatus("active");

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse update(Integer id, UpdateSupplierRequest request) {
        SupplierModel supplier = findSupplierOrThrow(id);

        String normalizedName = normalizeRequiredText(request.getName(), "Supplier name is required.");
        String normalizedContactPerson = normalizeNullableText(request.getContactPerson());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedAddress = normalizeNullableText(request.getAddress());
        String normalizedStatus = normalizeRequiredText(request.getStatus(), "Status is required.");

        validateDuplicateName(normalizedName, id);

        supplier.setName(normalizedName);
        supplier.setContactPerson(normalizedContactPerson);
        supplier.setPhone(normalizedPhone);
        supplier.setAddress(normalizedAddress);
        supplier.setStatus(normalizedStatus);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        SupplierModel supplier = findSupplierOrThrow(id);

        // TODO: Validate whether the supplier is referenced by Purchase Orders or Import Orders before allowing deletion.

        supplierRepository.delete(supplier);
    }

    @Override
    public SupplierResponse getById(Integer id) {
        return supplierMapper.toResponse(findSupplierOrThrow(id));
    }

    @Override
    public List<SupplierResponse> getAll() {
        return supplierRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Override
    public Page<SupplierResponse> getPage(PageRequestDTO pageRequest, String status) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<SupplierModel> specification = (root, ignored, cb) -> cb.conjunction();
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("contactPerson")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern)
            ));
        }
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase(Locale.ROOT)));
        }
        return supplierRepository.findAll(
                        specification,
                        query.toPageable("id", Sort.Direction.ASC, Set.of("id", "name", "status")))
                .map(supplierMapper::toResponse);
    }

    private SupplierModel findSupplierOrThrow(Integer id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found."));
    }

    private void validateDuplicateName(String name, Integer currentId) {
        boolean exists = currentId == null
                ? supplierRepository.existsByNameIgnoreCase(name)
                : supplierRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);

        if (exists) {
            throw new ConflictException("Supplier name already exists.");
        }
    }

    private String normalizePhone(String phone) {
        String normalized = normalizeWhitespace(phone);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("Invalid phone number.");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
