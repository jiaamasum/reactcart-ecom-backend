package org.masumjia.reactcartecom.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.settings.dto.SettingsDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/settings")
@Tag(name = "Settings (Admin)")
public class SettingsAdminController {
    private final StoreSettingsRepository repo;

    public SettingsAdminController(StoreSettingsRepository repo) {
        this.repo = repo;
    }

    private StoreSettings ensure() {
        return repo.findById("settings-1").orElseGet(() -> {
            StoreSettings s = new StoreSettings();
            s.setId("settings-1");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            return repo.save(s);
        });
    }

    // 1) Store Information
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/store")
    @Operation(summary = "Get store information", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.StoreInfoView>> getStore() {
        StoreSettings s = ensure();
        SettingsDtos.StoreInfoView v = new SettingsDtos.StoreInfoView(s.getStoreName(), s.getStoreDescription(), s.getStoreEmail(), s.getStorePhone(), s.getStoreAddress());
        return ResponseEntity.ok(ApiResponse.success(v));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/store")
    @Operation(summary = "Update store information", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.StoreInfoView>> putStore(@Valid @RequestBody SettingsDtos.StoreInfoRequest req) {
        StoreSettings s = ensure();
        s.setStoreName(req.storeName());
        s.setStoreDescription(req.storeDescription());
        s.setStoreEmail(req.storeEmail());
        s.setStorePhone(req.storePhone());
        s.setStoreAddress(req.storeAddress());
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        SettingsDtos.StoreInfoView v = new SettingsDtos.StoreInfoView(s.getStoreName(), s.getStoreDescription(), s.getStoreEmail(), s.getStorePhone(), s.getStoreAddress());
        return ResponseEntity.ok(ApiResponse.success(v, java.util.Map.of("message", "Store settings updated")));
    }

    // 2) SEO Settings
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/seo")
    @Operation(summary = "Get SEO settings", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.SeoView>> getSeo() {
        StoreSettings s = ensure();
        SettingsDtos.SeoView v = new SettingsDtos.SeoView(s.getMetaTitle(), s.getMetaDescription(), s.getMetaKeywords(), s.getOgImageUrl());
        return ResponseEntity.ok(ApiResponse.success(v));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/seo")
    @Operation(summary = "Update SEO settings", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.SeoView>> putSeo(@Valid @RequestBody SettingsDtos.SeoRequest req) {
        StoreSettings s = ensure();
        s.setMetaTitle(req.metaTitle());
        s.setMetaDescription(req.metaDescription());
        s.setMetaKeywords(req.metaKeywords());
        s.setOgImageUrl(req.ogImageUrl());
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        SettingsDtos.SeoView v = new SettingsDtos.SeoView(s.getMetaTitle(), s.getMetaDescription(), s.getMetaKeywords(), s.getOgImageUrl());
        return ResponseEntity.ok(ApiResponse.success(v, java.util.Map.of("message", "SEO settings updated")));
    }

    // 3) Currency Settings
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/currency")
    @Operation(summary = "Get currency settings", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.CurrencyView>> getCurrency() {
        StoreSettings s = ensure();
        return ResponseEntity.ok(ApiResponse.success(new SettingsDtos.CurrencyView(s.getDefaultCurrency())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/currency")
    @Operation(summary = "Update currency settings", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<SettingsDtos.CurrencyView>> putCurrency(@Valid @RequestBody SettingsDtos.CurrencyRequest req) {
        StoreSettings s = ensure();
        s.setDefaultCurrency(req.defaultCurrency());
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        return ResponseEntity.ok(ApiResponse.success(new SettingsDtos.CurrencyView(s.getDefaultCurrency()), java.util.Map.of("message", "Currency settings updated")));
    }
}

