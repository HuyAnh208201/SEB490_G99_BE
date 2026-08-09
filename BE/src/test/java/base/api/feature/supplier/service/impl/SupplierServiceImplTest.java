package base.api.feature.supplier.service.impl;

import base.api.feature.supplier.dto.request.CreateSupplierRequest;
import base.api.feature.supplier.dto.request.UpdateSupplierRequest;
import base.api.feature.supplier.dto.response.SupplierResponse;
import base.api.feature.supplier.mapper.SupplierMapper;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.shared.entity.SupplierModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SupplierServiceImpl} create / update / delete validation paths.
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private ISupplierRepository supplierRepository;

    @Mock
    private SupplierMapper supplierMapper;

    @InjectMocks
    private SupplierServiceImpl service;

    @Test
    void createSavesActiveSupplierWithNormalizedFields() {
        CreateSupplierRequest request = createRequest("  Acme  Co  ", "  Jane  ", "0912345678", "  Hanoi  ");
        when(supplierRepository.existsByNameIgnoreCase("Acme Co")).thenReturn(false);
        when(supplierRepository.save(any(SupplierModel.class))).thenAnswer(inv -> {
            SupplierModel saved = inv.getArgument(0);
            saved.setId(1);
            return saved;
        });
        SupplierResponse mapped = new SupplierResponse();
        mapped.setId(1);
        when(supplierMapper.toResponse(any(SupplierModel.class))).thenReturn(mapped);

        SupplierResponse response = service.create(request);

        assertEquals(1, response.getId());
        ArgumentCaptor<SupplierModel> captor = ArgumentCaptor.forClass(SupplierModel.class);
        verify(supplierRepository).save(captor.capture());
        assertEquals("Acme Co", captor.getValue().getName());
        assertEquals("Jane", captor.getValue().getContactPerson());
        assertEquals("0912345678", captor.getValue().getPhone());
        assertEquals("Hanoi", captor.getValue().getAddress());
        assertEquals("active", captor.getValue().getStatus());
    }

    @Test
    void createAllowsNullPhone() {
        CreateSupplierRequest request = createRequest("Acme", null, null, null);
        when(supplierRepository.existsByNameIgnoreCase("Acme")).thenReturn(false);
        when(supplierRepository.save(any(SupplierModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(any(SupplierModel.class))).thenReturn(new SupplierResponse());

        service.create(request);

        ArgumentCaptor<SupplierModel> captor = ArgumentCaptor.forClass(SupplierModel.class);
        verify(supplierRepository).save(captor.capture());
        assertNull(captor.getValue().getPhone());
    }

    @Test
    void createRejectsBlankName() {
        CreateSupplierRequest request = createRequest("  ", null, null, null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Supplier name is required.", error.getMessage());
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidPhone() {
        CreateSupplierRequest request = createRequest("Acme", null, "12345", null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Invalid phone number.", error.getMessage());
    }

    @Test
    void createRejectsDuplicateName() {
        CreateSupplierRequest request = createRequest("Acme", null, null, null);
        when(supplierRepository.existsByNameIgnoreCase("Acme")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals("Supplier name already exists.", error.getMessage());
    }

    @Test
    void updateSucceedsAndChangesStatus() {
        SupplierModel existing = supplier(2, "Old");
        when(supplierRepository.findById(2)).thenReturn(Optional.of(existing));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot("New Co", 2)).thenReturn(false);
        when(supplierRepository.save(any(SupplierModel.class))).thenAnswer(inv -> inv.getArgument(0));
        SupplierResponse mapped = new SupplierResponse();
        mapped.setStatus("inactive");
        when(supplierMapper.toResponse(any(SupplierModel.class))).thenReturn(mapped);

        UpdateSupplierRequest request = updateRequest("New Co", null, "0912345678", null, "inactive");
        SupplierResponse response = service.update(2, request);

        assertEquals("inactive", response.getStatus());
        assertEquals("inactive", existing.getStatus());
        assertEquals("New Co", existing.getName());
    }

    @Test
    void updateRejectsBlankStatus() {
        when(supplierRepository.findById(2)).thenReturn(Optional.of(supplier(2, "Acme")));
        UpdateSupplierRequest request = updateRequest("Acme", null, null, null, "  ");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.update(2, request));

        assertEquals("Status is required.", error.getMessage());
    }

    @Test
    void updateRejectsDuplicateNameOnOtherSupplier() {
        when(supplierRepository.findById(2)).thenReturn(Optional.of(supplier(2, "Old")));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot("Taken", 2)).thenReturn(true);
        UpdateSupplierRequest request = updateRequest("Taken", null, null, null, "active");

        ConflictException error = assertThrows(ConflictException.class, () -> service.update(2, request));

        assertEquals("Supplier name already exists.", error.getMessage());
    }

    @Test
    void updateRejectsInvalidPhone() {
        when(supplierRepository.findById(2)).thenReturn(Optional.of(supplier(2, "Acme")));
        UpdateSupplierRequest request = updateRequest("Acme", null, "abc", null, "active");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.update(2, request));

        assertEquals("Invalid phone number.", error.getMessage());
    }

    @Test
    void deleteRemovesExistingSupplier() {
        SupplierModel existing = supplier(3, "Acme");
        when(supplierRepository.findById(3)).thenReturn(Optional.of(existing));

        service.delete(3);

        verify(supplierRepository).delete(existing);
    }

    @Test
    void deleteThrowsWhenSupplierMissing() {
        when(supplierRepository.findById(99)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.delete(99));

        assertEquals("Supplier not found.", error.getMessage());
        verify(supplierRepository, never()).delete(any(SupplierModel.class));
    }

    private static CreateSupplierRequest createRequest(
            String name, String contactPerson, String phone, String address) {
        CreateSupplierRequest request = new CreateSupplierRequest();
        request.setName(name);
        request.setContactPerson(contactPerson);
        request.setPhone(phone);
        request.setAddress(address);
        return request;
    }

    private static UpdateSupplierRequest updateRequest(
            String name, String contactPerson, String phone, String address, String status) {
        UpdateSupplierRequest request = new UpdateSupplierRequest();
        request.setName(name);
        request.setContactPerson(contactPerson);
        request.setPhone(phone);
        request.setAddress(address);
        request.setStatus(status);
        return request;
    }

    private static SupplierModel supplier(Integer id, String name) {
        SupplierModel model = new SupplierModel();
        model.setId(id);
        model.setName(name);
        model.setStatus("active");
        return model;
    }
}
