package net.citotech.cito.admin;

import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.SettingsRegistry;
import net.citotech.cito.SettingsRegistry.Entry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only listing of the typed settings registry (audit O5), so an admin can see at a glance
 * what's actually configurable in the generic {@code settings} table, what type and default each
 * entry has, and what it's currently set to - without grepping through call sites for hardcoded
 * defaults. This sits under {@code /api/v2/admin/**}, which {@code SecurityConfig} already
 * restricts to ROLE_ADMIN, so no additional authorization annotation is needed here (matching
 * {@link FeatureFlagController}, the closest existing admin listing endpoint).
 *
 * <p>{@code storedValue} is the raw string from the {@code settings} table (null if no row
 * exists yet); {@code effectiveValue} is what {@link SettingsRegistry}'s typed accessor actually
 * returns for that entry right now, after parsing and safe-default fallback. Neither value is
 * printed for an entry whose name looks credential-shaped ({@link SettingsRegistry#isSecretLike});
 * none of the entries registered today are secrets, but this is a deliberate belt-and-suspenders
 * check rather than trusting every future registry entry to be non-sensitive.
 *
 * <p>Audit E3: now also carries a class-level {@code @PreAuthorize("hasRole('ADMIN')")} as
 * defense-in-depth reinforcement of the path-level rule above.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/settings/registry")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsRegistryController {

    private static final String REDACTED = "***REDACTED***";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SettingsRegistryController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<RegistryEntryResponse> list() {
        List<RegistryEntryResponse> response = new ArrayList<>();
        for (Entry entry : SettingsRegistry.all()) {
            response.add(toResponse(entry));
        }
        return response;
    }

    private RegistryEntryResponse toResponse(Entry entry) {
        boolean secret = SettingsRegistry.isSecretLike(entry.name());
        Setting stored = Common.getSettings(entry.name(), jdbcTemplate);
        String storedValue = stored == null ? null : stored.getSetting_value();
        boolean usingDefault = storedValue == null || storedValue.isBlank();
        String effectiveValue = secret ? REDACTED : effectiveValue(entry);
        return new RegistryEntryResponse(
            entry.name(),
            entry.type().name(),
            entry.group(),
            entry.description(),
            entry.defaultValue(),
            secret ? (storedValue == null ? null : REDACTED) : storedValue,
            effectiveValue,
            usingDefault
        );
    }

    private String effectiveValue(Entry entry) {
        return switch (entry.type()) {
            case BOOLEAN -> String.valueOf(SettingsRegistry.getBoolean(entry.name(), jdbcTemplate));
            case INTEGER -> String.valueOf(SettingsRegistry.getInt(entry.name(), jdbcTemplate));
            case DECIMAL -> SettingsRegistry.getDecimal(entry.name(), jdbcTemplate).toPlainString();
            case STRING -> SettingsRegistry.getString(entry.name(), jdbcTemplate);
        };
    }

    public record RegistryEntryResponse(
        String name,
        String type,
        String group,
        String description,
        String defaultValue,
        String storedValue,
        String effectiveValue,
        boolean usingDefault
    ) {
    }
}
