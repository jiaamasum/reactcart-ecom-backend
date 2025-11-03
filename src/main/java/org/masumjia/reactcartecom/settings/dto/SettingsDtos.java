package org.masumjia.reactcartecom.settings.dto;

import jakarta.validation.constraints.*;

public class SettingsDtos {
    public static record StoreInfoRequest(
            @NotBlank @Size(max = 200) String storeName,
            @Size(max = 5000) String storeDescription,
            @Email @Size(max = 255) String storeEmail,
            @Size(max = 50) String storePhone,
            @Size(max = 255) String storeAddress
    ) {}

    public static record SeoRequest(
            @Size(max = 255) String metaTitle,
            @Size(max = 5000) String metaDescription,
            @Size(max = 512) String metaKeywords,
            @Size(max = 2048) String ogImageUrl
    ) {}

    public static record CurrencyRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code") String defaultCurrency
    ) {}

    public static record StoreInfoView(String storeName, String storeDescription, String storeEmail, String storePhone, String storeAddress) {}
    public static record SeoView(String metaTitle, String metaDescription, String metaKeywords, String ogImageUrl) {}
    public static record CurrencyView(String defaultCurrency) {}
}

