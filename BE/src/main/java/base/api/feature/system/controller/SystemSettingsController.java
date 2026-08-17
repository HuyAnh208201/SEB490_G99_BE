package base.api.feature.system.controller;

import base.api.feature.category.dto.response.CategoryResponse;
import base.api.feature.category.mapper.CategoryMapper;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.system.dto.request.UpdateMembershipTierRequest;
import base.api.feature.system.dto.request.UpdateShortDateCategoriesRequest;
import base.api.feature.system.dto.response.MembershipTierResponse;
import base.api.feature.system.service.IMembershipTierService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.entity.CategoryModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System settings / membership tiers")
public class SystemSettingsController extends BaseAPIController {

    @Autowired
    private IMembershipTierService membershipTierService;

    @Autowired
    private ICategoryRepository categoryRepository;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "Current server time")
    @GetMapping("time")
    public ResponseEntity<TFUResponse<Map<String, Long>>> time() {
        return success(Map.of("epochMs", System.currentTimeMillis()));
    }

    @Operation(summary = "System settings summary")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("settings")
    public ResponseEntity<TFUResponse<Map<String, Object>>> settings() {
        return success(Map.of(
                "module", "system",
                "screen", "System Settings",
                "tiersEditable", true,
                "pointRatesNote", "Point earn/redeem rates are configured on the server."
        ));
    }

    @Operation(summary = "List membership tiers")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("membership-tiers")
    public ResponseEntity<TFUResponse<List<MembershipTierResponse>>> listTiers() {
        return success(membershipTierService.listTiers());
    }

    @Operation(summary = "Update a membership tier (code is immutable)")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @PutMapping("membership-tiers/{id}")
    public ResponseEntity<TFUResponse<MembershipTierResponse>> updateTier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMembershipTierRequest request) {
        return success(membershipTierService.updateTier(id, request), "Membership tier updated.");
    }

    @Operation(summary = "List all categories with short-date flags (for System Settings)")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("short-date-categories")
    public ResponseEntity<TFUResponse<List<CategoryResponse>>> listShortDateCategories() {
        List<CategoryResponse> data = categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(categoryMapper::toResponse)
                .toList();
        return success(data);
    }

    @Operation(summary = "Set which categories are short-date (replaces previous selection)")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @PutMapping("short-date-categories")
    @Transactional
    public ResponseEntity<TFUResponse<List<CategoryResponse>>> updateShortDateCategories(
            @RequestBody UpdateShortDateCategoriesRequest request) {
        Set<Integer> selected = new HashSet<>(
                request == null || request.getCategoryIds() == null
                        ? List.of()
                        : request.getCategoryIds());
        List<CategoryModel> all = categoryRepository.findAll();
        for (CategoryModel category : all) {
            category.setShortDate(selected.contains(category.getId()));
        }
        categoryRepository.saveAll(all);

        // Short-date SKUs are supplier-direct — remove any central warehouse rows.
        jdbcTemplate.update("""
                DELETE wi FROM warehouse_inventory wi
                INNER JOIN products p ON p.id = wi.product_id
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.short_date = 1
                """);

        List<CategoryResponse> data = categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(categoryMapper::toResponse)
                .toList();
        return success(data, "Short-date categories updated.");
    }
}
