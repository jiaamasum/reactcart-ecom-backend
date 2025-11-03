package org.masumjia.reactcartecom.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings (Public)")
public class SettingsPublicController {
    private final StoreSettingsRepository repo;

    public SettingsPublicController(StoreSettingsRepository repo) {
        this.repo = repo;
    }

    public record PublicSettingsView(
            String storeName,
            String storeDescription,
            String storeEmail,
            String storePhone,
            String storeAddress,
            String metaTitle,
            String metaDescription,
            String metaKeywords,
            String ogImageUrl,
            String defaultCurrency
    ) {}

    private StoreSettings ensure() {
        return repo.findById("settings-1").orElseGet(() -> {
            StoreSettings s = new StoreSettings();
            s.setId("settings-1");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            return repo.save(s);
        });
    }

    @GetMapping
    @Operation(summary = "Get public store settings")
    public ResponseEntity<ApiResponse<PublicSettingsView>> get() {
        StoreSettings s = ensure();
        PublicSettingsView view = new PublicSettingsView(
                s.getStoreName(), s.getStoreDescription(), s.getStoreEmail(), s.getStorePhone(), s.getStoreAddress(),
                s.getMetaTitle(), s.getMetaDescription(), s.getMetaKeywords(), s.getOgImageUrl(),
                s.getDefaultCurrency()
        );
        return ResponseEntity.ok(ApiResponse.success(view));
    }
}

