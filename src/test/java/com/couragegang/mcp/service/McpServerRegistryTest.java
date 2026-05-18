package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.RegisterMcpServerRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerRegistryTest {

  @Test
  void registerAndList() {
    var registry = new McpServerRegistry();
    registry.register(new RegisterMcpServerRequest("notion", "stdio", null));
    assertFalse(registry.list().isEmpty());
    assertTrue(registry.list().stream().anyMatch(s -> "notion".equals(s.name())));
  }
}
