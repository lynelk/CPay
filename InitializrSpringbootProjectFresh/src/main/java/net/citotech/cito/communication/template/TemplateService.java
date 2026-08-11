package net.citotech.cito.communication.template;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Template catalog service for {@code communication_message_templates} (V51, track B3, ISO domain
 * mapping: communication/template). A template is a channel-scoped, {placeholder}-based subject/
 * body pair keyed by {@code template_key + channel}; {@link #render} substitutes the caller's
 * context map into both fields.
 *
 * <p>Rendering is deliberately dependency-free: placeholders are replaced literally with the
 * context values, and any placeholder with no context value is replaced with an empty string — a
 * literal, visible gap — rather than silently dropping the surrounding text. Templates are additive
 * to the existing settings-driven SMS/email flows — nothing reads them unless a caller explicitly
 * routes through this service.
 */
@Service
public class TemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)\\}");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TemplateService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TemplateRow> list(String channel) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql =
                "SELECT id, template_key, channel, subject_template, body_template, status, created_at,"
                        + " updated_at FROM communication_message_templates";
        if (channel != null && !channel.isBlank()) {
            sql += " WHERE channel=:channel";
            p.addValue("channel", channel.trim().toUpperCase());
        }
        sql += " ORDER BY template_key ASC, channel ASC";
        return jdbcTemplate.query(sql, p, this::mapRow);
    }

    public Optional<TemplateRow> find(String templateKey, String channel) {
        List<TemplateRow> rows =
                jdbcTemplate.query(
                        "SELECT id, template_key, channel, subject_template, body_template, status,"
                                + " created_at, updated_at FROM communication_message_templates"
                                + " WHERE template_key=:template_key AND channel=:channel LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("template_key", templateKey)
                                .addValue("channel", normalizeChannel(channel)),
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Renders the active template for {@code templateKey} + {@code channel} with {@code context}.
     * Throws a {@link PaymentGatewayException} when the template does not exist or is not ACTIVE —
     * a caller that explicitly requested a template must not silently fall back to a different
     * body. The rendered record carries both the subject (nullable for channels without one, e.g.
     * SMS) and the body.
     */
    public RenderedTemplate render(
            String templateKey, String channel, Map<String, String> context) {
        TemplateRow template =
                find(templateKey, channel)
                        .orElseThrow(
                                () ->
                                        new PaymentGatewayException(
                                                "Unknown message template "
                                                        + templateKey
                                                        + " for channel "
                                                        + normalizeChannel(channel)));
        if (!"ACTIVE".equals(template.status())) {
            throw new PaymentGatewayException("Message template " + templateKey + " is not active");
        }
        Map<String, String> safe = context == null ? Map.of() : context;
        return new RenderedTemplate(
                templateKey,
                normalizeChannel(channel),
                template.subjectTemplate() == null
                        ? null
                        : substitute(template.subjectTemplate(), safe),
                substitute(template.bodyTemplate(), safe));
    }

    /** Upserts a template and returns the persisted row. */
    public TemplateRow save(
            String templateKey, String channel, String subjectTemplate, String bodyTemplate) {
        if (templateKey == null || templateKey.isBlank()) {
            throw new PaymentGatewayException("templateKey is required");
        }
        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            throw new PaymentGatewayException("bodyTemplate is required");
        }
        String normalizedKey = templateKey.trim();
        String normalizedChannel = normalizeChannel(channel);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("template_key", normalizedKey);
        p.addValue("channel", normalizedChannel);
        p.addValue("subject_template", subjectTemplate);
        p.addValue("body_template", bodyTemplate.trim());
        jdbcTemplate.update(
                "INSERT INTO communication_message_templates (template_key, channel, subject_template,"
                        + " body_template) VALUES (:template_key, :channel, :subject_template,"
                        + " :body_template) ON DUPLICATE KEY UPDATE subject_template=VALUES(subject_template),"
                        + " body_template=VALUES(body_template), status='ACTIVE'",
                p);
        return find(normalizedKey, normalizedChannel)
                .orElseThrow(() -> new IllegalStateException("Failed to persist template"));
    }

    /** Soft-toggles a template to INACTIVE (or back to ACTIVE). Returns rows affected. */
    public int setStatus(long id, String status) {
        String normalizedStatus = "ACTIVE".equalsIgnoreCase(status) ? "ACTIVE" : "INACTIVE";
        return jdbcTemplate.update(
                "UPDATE communication_message_templates SET status=:status WHERE id=:id",
                new MapSqlParameterSource("id", id).addValue("status", normalizedStatus));
    }

    private String substitute(String template, Map<String, String> context) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = context.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeChannel(String channel) {
        return channel == null || channel.isBlank() ? "SMS" : channel.trim().toUpperCase();
    }

    private TemplateRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TemplateRow(
                rs.getLong("id"),
                rs.getString("template_key"),
                rs.getString("channel"),
                rs.getString("subject_template"),
                rs.getString("body_template"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    public record TemplateRow(
            long id,
            String templateKey,
            String channel,
            String subjectTemplate,
            String bodyTemplate,
            String status,
            String createdAt,
            String updatedAt) {}

    public record RenderedTemplate(
            String templateKey, String channel, String subject, String body) {}
}
