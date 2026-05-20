package com.couragegang.mcp.integration;

import jakarta.inject.Singleton;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Post-MVP: HTTP call to policy-service apply-install-pack. */
@Singleton
public final class PolicyPackApplier {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyPackApplier.class);

    public boolean applyInstallPack(UUID orgId, UUID workspaceId, UUID installationId, String connectorKey) {
        LOG.info(
                "policy pack apply stub: org={} workspace={} installation={} connector={}",
                orgId,
                workspaceId,
                installationId,
                connectorKey);
        return true;
    }

    public void revokeInstallPack(UUID installationId) {
        LOG.info("policy pack revoke stub: installation={}", installationId);
    }
}
