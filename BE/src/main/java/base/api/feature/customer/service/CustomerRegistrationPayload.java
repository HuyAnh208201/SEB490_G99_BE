package base.api.feature.customer.service;

public record CustomerRegistrationPayload(String phone, String fullName, String passwordHash) {
}
