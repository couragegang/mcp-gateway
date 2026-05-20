package com.couragegang.mcp.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

@Singleton
public final class CatalogRepository {

    private final DataSource dataSource;
    private final ObjectMapper json;

    public CatalogRepository(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
    }

    public List<CatalogRow> listPublished() throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT connector_key, display_name, description, connection_form_schema::text,
                               policy_pack_version::text
                        FROM mcp_catalog_tools WHERE status = 'published' ORDER BY display_name
                        """)) {
            var out = new ArrayList<CatalogRow>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        }
    }

    public Optional<CatalogRow> findPublished(String connectorKey) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT connector_key, display_name, description, connection_form_schema::text,
                               policy_pack_version::text
                        FROM mcp_catalog_tools
                        WHERE connector_key = ? AND status = 'published'
                        """)) {
            ps.setString(1, connectorKey);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    private CatalogRow map(java.sql.ResultSet rs) throws SQLException {
        return new CatalogRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                parseJson(rs.getString(4)),
                rs.getString(5));
    }

    private Map<String, Object> parseJson(String raw) throws SQLException {
        try {
            return json.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("invalid json", e);
        }
    }

    public record CatalogRow(
            String connectorKey,
            String displayName,
            String description,
            Map<String, Object> connectionFormSchema,
            String policyPackVersion) {}
}
