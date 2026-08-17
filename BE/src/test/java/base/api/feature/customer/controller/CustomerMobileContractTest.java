package base.api.feature.customer.controller;

import base.api.feature.customer.dto.CustomerAuthDtos;
import base.api.feature.customer.dto.CustomerDtos;
import base.api.feature.customer.dto.CustomerPromotionDtos;
import base.api.feature.customer.service.CustomerAccountService;
import base.api.feature.customer.service.CustomerAuthService;
import base.api.feature.customer.service.CustomerPromotionService;
import base.api.shared.entity.UserModel;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerMobileContractTest {

    private CustomerAuthService authService;
    private CustomerAccountService accountService;
    private CustomerPromotionService promotionService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc authMvc;
    private MockMvc customerMvc;
    private MockMvc promotionMvc;

    @BeforeEach
    void setUp() {
        authService = mock(CustomerAuthService.class);
        accountService = mock(CustomerAccountService.class);
        promotionService = mock(CustomerPromotionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        authMvc = MockMvcBuilders.standaloneSetup(new CustomerAuthController(authService)).build();
        customerMvc = MockMvcBuilders
                .standaloneSetup(new CustomerController(accountService, currentUserProvider))
                .build();
        promotionMvc = MockMvcBuilders
                .standaloneSetup(new CustomerPromotionController(promotionService))
                .build();
    }

    @Test
    void loginReturnsRawMobileAuthResponseWithoutWebEnvelope() throws Exception {
        CustomerAuthDtos.AuthResponse response = new CustomerAuthDtos.AuthResponse();
        response.setToken("jwt-token");
        response.setUserId(7L);
        response.setPhone("0912345678");
        response.setEmail("customer@example.com");
        response.setFullName("Customer One");
        response.setPoints(100L);
        response.setTierCode("SILVER");
        response.setTierName("Silver");
        when(authService.login(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        authMvc.perform(post("/api/customer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"customer@example.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token", is("jwt-token")))
                .andExpect(jsonPath("$.userId", is(7)))
                .andExpect(jsonPath("$.tierCode", is("SILVER")))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void profileAndPromotionRoutesPreserveMobilePathsAndShapes() throws Exception {
        UserModel user = new UserModel();
        user.setId(7L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);

        CustomerDtos.ProfileResponse profile = new CustomerDtos.ProfileResponse();
        profile.setId(7L);
        profile.setPhone("0912345678");
        profile.setQrPayload("0912345678");
        when(accountService.getProfile(user)).thenReturn(profile);

        CustomerPromotionDtos.PromotionResponse promotion =
                new CustomerPromotionDtos.PromotionResponse();
        promotion.setId(3L);
        promotion.setName("Summer Sale");
        when(promotionService.listActive()).thenReturn(List.of(promotion));

        customerMvc.perform(get("/api/customer/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(7)))
                .andExpect(jsonPath("$.qrPayload", is("0912345678")));

        promotionMvc.perform(get("/api/customer/promotions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(3)))
                .andExpect(jsonPath("$[0].name", is("Summer Sale")));
    }

    @Test
    void controllersDelegateEveryMobileEndpoint() {
        CustomerAuthController authController = new CustomerAuthController(authService);
        CustomerController customerController = new CustomerController(accountService, currentUserProvider);
        CustomerPromotionController promotionController = new CustomerPromotionController(promotionService);
        UserModel user = new UserModel();
        user.setId(7L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);

        CustomerAuthDtos.RegisterRequestOtp registerRequest = new CustomerAuthDtos.RegisterRequestOtp();
        CustomerAuthDtos.RegisterVerify registerVerify = new CustomerAuthDtos.RegisterVerify();
        CustomerAuthDtos.LoginRequest login = new CustomerAuthDtos.LoginRequest();
        CustomerAuthDtos.ForgotRequestOtp forgotRequest = new CustomerAuthDtos.ForgotRequestOtp();
        CustomerAuthDtos.ForgotVerify forgotVerify = new CustomerAuthDtos.ForgotVerify();
        CustomerDtos.UpdateProfileRequest update = new CustomerDtos.UpdateProfileRequest();

        authController.requestRegisterOtp(registerRequest);
        authController.verifyRegister(registerVerify);
        authController.login(login);
        authController.requestForgotOtp(forgotRequest);
        authController.verifyForgot(forgotVerify);
        customerController.me();
        customerController.updateMe(update);
        customerController.qr();
        customerController.pointHistory("ALL", 0, 20);
        customerController.loyaltyConfig();
        customerController.tiers();
        promotionController.active();

        verify(authService).requestRegisterOtp(registerRequest);
        verify(authService).verifyRegister(registerVerify);
        verify(authService).login(login);
        verify(authService).requestForgotOtp(forgotRequest);
        verify(authService).verifyForgot(forgotVerify);
        verify(accountService).getProfile(user);
        verify(accountService).updateProfile(user, update);
        verify(accountService).qr(user);
        verify(accountService).pointHistory(7L, "ALL", 0, 20);
        verify(accountService).loyaltyConfig();
        verify(accountService).listTiers(user);
        verify(promotionService).listActive();
    }
}
