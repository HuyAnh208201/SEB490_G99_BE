package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashierCustomerTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IUserService userService;

    @Mock
    private MembershipTierRepository membershipTierRepository;

    @InjectMocks
    private CashierServiceImpl service;

    @Test
    void blankKeywordDoesNotHitTheDatabase() {
        assertTrue(service.searchCustomers("   ").isEmpty());
        assertTrue(service.searchCustomers(null).isEmpty());
        verify(userRepository, never()).searchCustomers(any(), any());
    }

    @Test
    void partialKeywordReturnsMatchesWithoutNeedingTheFullPhone() {
        when(userRepository.searchCustomers(eq("9123"), any(PageRequest.class)))
                .thenReturn(List.of(customer(7L, "Trần Bảo", "0909123456", 40L)));

        List<CustomerLookupResponse> results = service.searchCustomers("  9123  ");

        assertEquals(1, results.size());
        assertEquals("0909123456", results.get(0).getPhone());
        assertEquals(40L, results.get(0).getTotalPoints());
    }

    @Test
    void quickCreateRejectsAPhoneThatBelongsToStaff() {
        UserModel cashier = customer(3L, "Lê Cashier", "0911222333", 0L);
        cashier.setRole(UserRole.CASHIER);
        when(userService.getOrCreateGuestByPhone("0911222333", "Ai Đó")).thenReturn(cashier);

        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFullName("Ai Đó");
        request.setPhone("0911222333");

        BadRequestException error =
                assertThrows(BadRequestException.class, () -> service.createCustomer(request));
        assertTrue(error.getMessage().contains("staff account"));
    }

    @Test
    void quickCreateReturnsTheNewCustomer() {
        when(userService.getOrCreateGuestByPhone("0909123456", "Trần Bảo"))
                .thenReturn(customer(7L, "Trần Bảo", "0909123456", 0L));

        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFullName("Trần Bảo");
        request.setPhone("0909123456");

        CustomerLookupResponse created = service.createCustomer(request);

        assertEquals(7L, created.getCustomerId());
        assertEquals("Trần Bảo", created.getFullName());
    }

    private UserModel customer(Long id, String fullName, String phone, Long points) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail("walkin_" + phone + "@guest.chainstore.com");
        user.setPoints(points);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }
}
