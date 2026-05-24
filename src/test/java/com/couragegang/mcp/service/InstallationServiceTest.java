package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.mcp.api.dto.McpModels.InstallationCreateRequest;
import com.couragegang.mcp.api.dto.McpModels.InstallationUpdateRequest;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.integration.AuditClient;
import com.couragegang.mcp.api.dto.McpModels.HealthCheckResult;
import com.couragegang.mcp.integration.ConnectorRuntimeClient;
import com.couragegang.mcp.integration.PolicyPackApplier;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.CatalogRepository;
import com.couragegang.mcp.repo.InstallationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

    @Mock
    CatalogRepository catalog;

    @Mock
    InstallationRepository installations;

    @Mock
    ConnectionFormSplitter formSplitter;

    @Mock
    SecretsClient secrets;

    @Mock
    PolicyPackApplier policyPack;

    @Mock
    AuditClient audit;

    @Mock
    ConnectorRuntimeClient connectorRuntime;

    InstallationService svc;

    UUID orgId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new InstallationService(
                catalog, installations, formSplitter, secrets, policyPack, audit, connectorRuntime);
        lenient()
                .when(connectorRuntime.resolveRuntimeBaseUrl(eq("notion"), any()))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        lenient()
                .when(connectorRuntime.normalizeConfig(any(), eq("notion"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    var cfg = new java.util.HashMap<>((Map<String, Object>) invocation.getArgument(2));
                    var raw = cfg.get("default_database_id");
                    if (raw != null) {
                        var s = String.valueOf(raw).strip();
                        if (s.isBlank()) {
                            cfg.remove("default_database_id");
                        }
                    }
                    return cfg;
                });
    }

    @Test
    void createTrimsDisplayLabel() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of()));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), eq("My Notion"), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(new InstallationRepository.InstallationRow(
                        instId, wsId, "notion", "My Notion", "active", Instant.now())));

        svc.create(orgId, wsId, new InstallationCreateRequest("notion", "  My Notion  ", Map.of(), null), null);

        verify(installations).insert(any(), any(), any(), eq("My Notion"), any(), any(), any());
    }

    @Test
    void createInstallation() throws Exception {
        var schema = Map.<String, Object>of("fields", List.of());
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", schema, 1, Map.of("rules", List.of()), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(eq(schema), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(
                        Map.of("integration_token", "secret_x"), Map.of()));
        when(secrets.storeCredentials(any(), any(), eq("notion"), any()))
                .thenReturn(Optional.of("secrets:" + UUID.randomUUID()));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(new InstallationRepository.InstallationRow(
                        instId, wsId, "notion", "Notion", "active", Instant.now())));

        var result =
                svc.create(
                        orgId,
                        wsId,
                        new InstallationCreateRequest(
                                "notion", "Notion", Map.of("integration_token", "secret_x"), null),
                        null);

        assertThat(result.connectorKey()).isEqualTo("notion");
    }

    @Test
    void createUsesLocalSecretWhenNoSecretsInForm() throws Exception {
        when(catalog.findPublished("slack"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "slack", "Slack", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "slack")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of("x", 1)));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), eq("local:none"), any(), any()))
                .thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(new InstallationRepository.InstallationRow(
                        instId, wsId, "slack", "slack", "active", Instant.now())));

        svc.create(orgId, wsId, new InstallationCreateRequest("slack", null, Map.of(), null), null);

        verify(secrets, org.mockito.Mockito.never()).storeCredentials(any(), any(), any(), any());
    }

    @Test
    void createFailsWhenSecretsStoreReturnsEmpty() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of("token", "s"), Map.of()));
        when(secrets.storeCredentials(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                svc.create(
                                        orgId,
                                        wsId,
                                        new InstallationCreateRequest("notion", "N", Map.of("token", "s"), null),
                                        null))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void createConflictWhenAlreadyInstalled() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        var existingId = UUID.randomUUID();
        when(installations.findByWorkspaceAndConnector(wsId, "notion"))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        existingId,
                                        orgId,
                                        wsId,
                                        "notion",
                                        "N",
                                        "active",
                                        Map.of(),
                                        "secrets:r")));

        assertThatThrownBy(
                        () -> svc.create(orgId, wsId, new InstallationCreateRequest("notion", "N", Map.of(), null), null))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void listInstallations() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(
                        List.of(
                                new InstallationRepository.InstallationRow(
                                        id, wsId, "notion", "Notion", "active", Instant.now())));

        var page = svc.list(wsId);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void deleteInstallation() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "Notion", "active", Map.of(), "secrets:ref")));
        doNothing().when(installations).updateStatus(eq(id), eq("revoked"));
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());

        svc.delete(wsId, id);

        verify(policyPack).revokeInstallPack(eq(id));
        verify(secrets).revoke(eq("secrets:ref"));
    }

    @Test
    void healthNotionOk() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "N", "active", Map.of(), "secrets:r")));
        when(secrets.resolvePayload("secrets:r")).thenReturn(Map.of("integration_token", "t"));
        when(connectorRuntime.resolveRuntimeBaseUrl(eq("notion"), any()))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.probeHealth(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HealthCheckResult(true, "ok"));

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
    }

    @Test
    void createFailsWhenPolicyApplyFails() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of("t", "s"), Map.of()));
        when(secrets.storeCredentials(any(), any(), any(), any())).thenReturn(Optional.of("secrets:r"));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(false);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(policyPack).revokeInstallPack(any());
        doNothing().when(secrets).revoke(any());

        assertThatThrownBy(
                        () ->
                                svc.create(
                                        orgId,
                                        wsId,
                                        new InstallationCreateRequest("notion", "N", Map.of("t", "s"), null),
                                        null))
                .isInstanceOf(McpApiException.class);

        verify(installations, org.mockito.Mockito.atLeastOnce()).updateStatus(eq(instId), eq("revoked"));
    }

    @Test
    void healthNotionFailureMarksError() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "N", "active", Map.of(), "secrets:r")));
        when(secrets.resolvePayload("secrets:r")).thenReturn(Map.of("integration_token", "t"));
        when(connectorRuntime.resolveRuntimeBaseUrl(eq("notion"), any()))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.probeHealth(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HealthCheckResult(false, "bad"));
        doNothing().when(installations).insertHealthCheck(any(), any(), any());
        doNothing().when(installations).updateStatus(any(), any());

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isFalse();
        verify(installations).updateStatus(id, "error");
    }

    @Test
    void healthNotionOkRecoversErrorStatus() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "N", "error", Map.of(), "secrets:r")));
        when(secrets.resolvePayload("secrets:r")).thenReturn(Map.of("integration_token", "t"));
        when(connectorRuntime.resolveRuntimeBaseUrl(eq("notion"), any()))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.probeHealth(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HealthCheckResult(true, "ok"));
        doNothing().when(installations).insertHealthCheck(any(), any(), any());
        doNothing().when(installations).updateStatus(any(), any());

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
        verify(installations).updateStatus(id, "active");
    }

    @Test
    void listWrapsSqlException() throws Exception {
        when(installations.listByWorkspace(wsId)).thenThrow(new java.sql.SQLException("db"));

        assertThatThrownBy(() -> svc.list(wsId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createConnectorNotFound() throws Exception {
        when(catalog.findPublished("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                svc.create(
                                        orgId,
                                        wsId,
                                        new InstallationCreateRequest("ghost", null, Map.of(), null),
                                        null))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void healthUsesRuntimeFallbackWhenCatalogUrlMissing() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id)).thenReturn(Optional.of(detailRow(id, "active", Map.of(), "secrets:r")));
        when(catalog.findPublished("notion"))
                .thenReturn(
                        Optional.of(
                                new CatalogRepository.CatalogRow(
                                        "notion", "N", "d", Map.of(), 1, Map.of(), null, "http")));
        when(connectorRuntime.resolveRuntimeBaseUrl("notion", null))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.probeHealth(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HealthCheckResult(true, "ok"));
        doNothing().when(installations).insertHealthCheck(any(), any(), any());

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
    }

    @Test
    void healthGenericConnectorSkipsProbe() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "custom", "C", "error", Map.of(), "local:none")));

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
        verify(connectorRuntime, org.mockito.Mockito.never()).probeHealth(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getDetailReturnsSchemaAndSecretsFlag() throws Exception {
        var id = UUID.randomUUID();
        var schema = Map.<String, Object>of("fields", List.of());
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "active", Map.of("k", "v"), "secrets:ref")));
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", schema, 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findById(wsId, id))
                .thenReturn(Optional.of(installationRow(id, "notion", "Notion", "active")));

        var res = svc.getDetail(wsId, id);

        assertThat(res.secretsConfigured()).isTrue();
        assertThat(res.connectionFormSchema()).isEqualTo(schema);
        assertThat(res.config()).containsEntry("k", "v");
    }

    @Test
    void getDetailLocalSecretNotConfigured() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "slack", "active", Map.of(), "local:none")));
        when(catalog.findPublished("slack"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "slack", "Slack", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findById(wsId, id))
                .thenReturn(Optional.of(installationRow(id, "slack", "Slack", "active")));

        assertThat(svc.getDetail(wsId, id).secretsConfigured()).isFalse();
    }

    @Test
    void getDetailRevokedNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "revoked", Map.of(), "secrets:r")));

        assertThatThrownBy(() -> svc.getDetail(wsId, id)).isInstanceOf(McpApiException.class);
    }

    @Test
    void getDetailInstallationMissing() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.getDetail(wsId, id)).isInstanceOf(McpApiException.class);
    }

    @Test
    void updateDisplayLabel() throws Exception {
        var id = UUID.randomUUID();
        var schema = Map.<String, Object>of("fields", List.of());
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "active", Map.of(), "secrets:r")));
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", schema, 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.updateDisplayLabel(wsId, id, "Renamed")).thenReturn(true);
        when(installations.findById(wsId, id))
                .thenReturn(Optional.of(installationRow(id, "notion", "Renamed", "active")));
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());

        var res = svc.update(wsId, id, new InstallationUpdateRequest("Renamed", null));

        assertThat(res.displayLabel()).isEqualTo("Renamed");
    }

    @Test
    void updateFormConfigAndSecrets() throws Exception {
        var id = UUID.randomUUID();
        var schema = Map.<String, Object>of("fields", List.of());
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "active", Map.of("old", 1), "secrets:old")));
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", schema, 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(formSplitter.split(eq(schema), any()))
                .thenReturn(
                        new ConnectionFormSplitter.SplitResult(
                                Map.of("integration_token", "tok-new"),
                                Map.of("default_database_id", "db-new")));
        doNothing().when(secrets).revoke("secrets:old");
        when(secrets.storeCredentials(orgId, wsId, "notion", Map.of("integration_token", "tok-new")))
                .thenReturn(Optional.of("secrets:new"));
        when(installations.updateConnectionConfig(eq(wsId), eq(id), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(true);
        when(installations.updateCredentialSecretRef(wsId, id, "secrets:new")).thenReturn(true);
        when(installations.findById(wsId, id))
                .thenReturn(Optional.of(installationRow(id, "notion", "Notion", "active")));
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());

        svc.update(wsId, id, new InstallationUpdateRequest(null, Map.of("integration_token", "tok-new")));

        verify(installations).updateConnectionConfig(eq(wsId), eq(id), any());
        verify(installations).updateCredentialSecretRef(wsId, id, "secrets:new");
    }

    @Test
    void createReactivatesRevokedInstallation() throws Exception {
        var existingId = UUID.randomUUID();
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", Map.of(), 1, Map.of("rules", List.of()), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion"))
                .thenReturn(Optional.of(detailRow(existingId, "revoked", Map.of(), "secrets:old")));
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of()));
        doNothing().when(secrets).revoke("secrets:old");
        doNothing().when(policyPack).revokeInstallPack(existingId);
        when(installations.reactivate(eq(wsId), eq(existingId), eq("Notion"), eq("local:none"), any(), any()))
                .thenReturn(true);
        when(policyPack.applyInstallPack(any(), any(), eq(existingId), any(), anyInt(), any(), any()))
                .thenReturn(true);
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, existingId))
                .thenReturn(Optional.of(installationRow(existingId, "notion", "Notion", "active")));

        var res = svc.create(orgId, wsId, new InstallationCreateRequest("notion", "Notion", Map.of(), null), null);

        assertThat(res.id()).isEqualTo(existingId);
        verify(installations, org.mockito.Mockito.never()).insert(any(), any(), any(), any(), any(), any(), any());
        verify(installations).reactivate(eq(wsId), eq(existingId), eq("Notion"), eq("local:none"), any(), any());
    }

    @Test
    void createReactivateFailsWhenRowMissing() throws Exception {
        var existingId = UUID.randomUUID();
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion"))
                .thenReturn(Optional.of(detailRow(existingId, "revoked", Map.of(), "secrets:old")));
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of()));
        doNothing().when(secrets).revoke("secrets:old");
        doNothing().when(policyPack).revokeInstallPack(existingId);
        when(installations.reactivate(any(), any(), any(), any(), any(), any())).thenReturn(false);
        doNothing().when(secrets).revoke("local:none");

        assertThatThrownBy(
                        () -> svc.create(orgId, wsId, new InstallationCreateRequest("notion", "N", Map.of(), null), null))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void createUniqueViolationReturnsConflict() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of()));
        when(installations.insert(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new java.sql.SQLException("duplicate", "23505", 23505));

        assertThatThrownBy(
                        () -> svc.create(orgId, wsId, new InstallationCreateRequest("notion", "N", Map.of(), null), null))
                .isInstanceOf(McpApiException.class)
                .matches(e -> "CONFLICT".equals(((McpApiException) e).body().code()), "CONFLICT code");
    }

    @Test
    void createNormalizesNotionDatabaseId() throws Exception {
        var dbUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of("default_database_id", dbUuid)));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), eq("local:none"), any(), any()))
                .thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(installationRow(instId, "notion", "notion", "active")));

        svc.create(orgId, wsId, new InstallationCreateRequest("notion", null, Map.of(), null), null);

        verify(installations)
                .insert(
                        eq(orgId),
                        eq(wsId),
                        eq("notion"),
                        eq("notion"),
                        eq("local:none"),
                        org.mockito.ArgumentMatchers.argThat(
                                cfg -> dbUuid.equalsIgnoreCase(String.valueOf(((Map<?, ?>) cfg).get("default_database_id")))),
                        any());
    }

    @Test
    void healthNotFoundWhenInstallationMissing() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.health(wsId, id)).isInstanceOf(McpApiException.class);
    }

    @Test
    void updateDisplayLabelNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detailRow(id, "active", Map.of(), "secrets:r")));
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));

        when(installations.updateDisplayLabel(wsId, id, "X")).thenReturn(false);

        assertThatThrownBy(() -> svc.update(wsId, id, new InstallationUpdateRequest("X", null)))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void createUsesExplicitPolicyPack() throws Exception {
        var customPack = Map.<String, Object>of("mode", "strict");
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of("default", true), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of()));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), eq(customPack), any()))
                .thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(installationRow(instId, "notion", "notion", "active")));

        svc.create(orgId, wsId, new InstallationCreateRequest("notion", null, Map.of(), customPack), null);

        verify(policyPack).applyInstallPack(any(), any(), any(), any(), anyInt(), eq(customPack), any());
    }

    @Test
    void createStripsBlankNotionDatabaseId() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of(), "http://mcp-notion:8091/v1/notion", "http")));
        when(installations.findByWorkspaceAndConnector(wsId, "notion")).thenReturn(Optional.empty());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of("default_database_id", "   ")));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        doNothing().when(installations).updateStatus(any(), any());
        doNothing().when(audit).emitInstallationEvent(any(), any(), any(), any(), any(), any());
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(installationRow(instId, "notion", "notion", "active")));

        svc.create(orgId, wsId, new InstallationCreateRequest("notion", null, Map.of(), null), null);

        verify(installations)
                .insert(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.argThat(cfg -> !((Map<?, ?>) cfg).containsKey("default_database_id")),
                        any());
    }

    private InstallationRepository.InstallationDetailRow detailRow(
            UUID id, String status, Map<String, Object> config, String secretRef) {
        return detailRow(id, "notion", status, config, secretRef);
    }

    private InstallationRepository.InstallationDetailRow detailRow(
            UUID id, String connectorKey, String status, Map<String, Object> config, String secretRef) {
        return new InstallationRepository.InstallationDetailRow(
                id, orgId, wsId, connectorKey, connectorKey, status, config, secretRef);
    }

    private InstallationRepository.InstallationRow installationRow(
            UUID id, String connector, String label, String status) {
        return new InstallationRepository.InstallationRow(id, wsId, connector, label, status, Instant.now());
    }
}
