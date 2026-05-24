package com.couragegang.mcp.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class InstallationRepository {

    private final DataSource dataSource;
    private final ObjectMapper json;

    public InstallationRepository(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
    }

    public UUID insert(
            UUID orgId,
            UUID workspaceId,
            String connectorKey,
            String displayLabel,
            String secretRef,
            Map<String, Object> connectionConfig,
            UUID installedBy)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO workspace_mcp_installations
                          (org_id, workspace_id, connector_key, display_label, credential_secret_ref,
                           connection_config, installed_by_user_id)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                        RETURNING id
                        """)) {
            ps.setObject(1, orgId);
            ps.setObject(2, workspaceId);
            ps.setString(3, connectorKey);
            ps.setString(4, displayLabel);
            ps.setString(5, secretRef);
            ps.setString(6, toJson(connectionConfig));
            if (installedBy == null) {
                ps.setNull(7, Types.OTHER);
            } else {
                ps.setObject(7, installedBy);
            }
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public Optional<InstallationDetailRow> findByWorkspaceAndConnector(UUID workspaceId, String connectorKey)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, org_id, workspace_id, connector_key, display_label, status,
                               connection_config::text, credential_secret_ref
                        FROM workspace_mcp_installations
                        WHERE workspace_id = ? AND connector_key = ?
                        """)) {
            ps.setObject(1, workspaceId);
            ps.setString(2, connectorKey);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new InstallationDetailRow(
                            rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            parseJson(rs.getString(7)),
                            rs.getString(8)));
                }
            }
        }
        return Optional.empty();
    }

    public boolean reactivate(
            UUID workspaceId,
            UUID installationId,
            String displayLabel,
            String secretRef,
            Map<String, Object> connectionConfig,
            UUID installedBy)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE workspace_mcp_installations
                        SET display_label = ?,
                            credential_secret_ref = ?,
                            connection_config = ?::jsonb,
                            status = 'active',
                            installed_at = now(),
                            installed_by_user_id = ?
                        WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setString(1, displayLabel);
            ps.setString(2, secretRef);
            ps.setString(3, toJson(connectionConfig));
            if (installedBy == null) {
                ps.setNull(4, Types.OTHER);
            } else {
                ps.setObject(4, installedBy);
            }
            ps.setObject(5, installationId);
            ps.setObject(6, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<InstallationRow> listByWorkspace(UUID workspaceId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, workspace_id, connector_key, display_label, status, installed_at
                        FROM workspace_mcp_installations
                        WHERE workspace_id = ? AND status <> 'revoked'
                        ORDER BY installed_at DESC
                        """)) {
            ps.setObject(1, workspaceId);
            var out = new ArrayList<InstallationRow>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapList(rs));
                }
            }
            return out;
        }
    }

    public Optional<InstallationRow> findById(UUID workspaceId, UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, workspace_id, connector_key, display_label, status, installed_at
                        FROM workspace_mcp_installations WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapList(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<InstallationDetailRow> findDetail(UUID workspaceId, UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, org_id, workspace_id, connector_key, display_label, status,
                               connection_config::text, credential_secret_ref
                        FROM workspace_mcp_installations WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new InstallationDetailRow(
                            rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            parseJson(rs.getString(7)),
                            rs.getString(8)));
                }
            }
        }
        return Optional.empty();
    }

    public void updateStatus(UUID id, String status) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("UPDATE workspace_mcp_installations SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, id);
            ps.executeUpdate();
        }
    }

    public boolean updateDisplayLabel(UUID workspaceId, UUID id, String displayLabel) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE workspace_mcp_installations
                        SET display_label = ?
                        WHERE id = ? AND workspace_id = ? AND status <> 'revoked'
                        """)) {
            ps.setString(1, displayLabel);
            ps.setObject(2, id);
            ps.setObject(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateConnectionConfig(UUID workspaceId, UUID id, Map<String, Object> config)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE workspace_mcp_installations
                        SET connection_config = ?::jsonb
                        WHERE id = ? AND workspace_id = ? AND status <> 'revoked'
                        """)) {
            ps.setString(1, toJson(config));
            ps.setObject(2, id);
            ps.setObject(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateCredentialSecretRef(UUID workspaceId, UUID id, String secretRef) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE workspace_mcp_installations
                        SET credential_secret_ref = ?
                        WHERE id = ? AND workspace_id = ? AND status <> 'revoked'
                        """)) {
            ps.setString(1, secretRef);
            ps.setObject(2, id);
            ps.setObject(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public void insertHealthCheck(UUID installationId, String result, Map<String, Object> details)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO mcp_health_checks (installation_id, result, details)
                        VALUES (?, ?, ?::jsonb)
                        """)) {
            ps.setObject(1, installationId);
            ps.setString(2, result);
            ps.setString(3, details == null ? null : toJson(details));
            ps.executeUpdate();
        }
    }

    private InstallationRow mapList(java.sql.ResultSet rs) throws SQLException {
        return new InstallationRow(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getTimestamp(6).toInstant());
    }

    private String toJson(Map<String, Object> value) throws SQLException {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new SQLException("invalid json", e);
        }
    }

    private Map<String, Object> parseJson(String raw) throws SQLException {
        try {
            return json.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("invalid json", e);
        }
    }

    public record InstallationRow(
            UUID id,
            UUID workspaceId,
            String connectorKey,
            String displayLabel,
            String status,
            Instant installedAt) {}

    public record InstallationDetailRow(
            UUID id,
            UUID orgId,
            UUID workspaceId,
            String connectorKey,
            String displayLabel,
            String status,
            Map<String, Object> connectionConfig,
            String credentialSecretRef) {}
}
