package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import java.sql.SQLException;

import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.repo.CatalogRepository;
import com.couragegang.mcp.repo.CatalogRepository.CatalogRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    CatalogRepository catalog;

    CatalogService svc;

    @BeforeEach
    void setUp() {
        svc = new CatalogService(catalog);
    }

    @Test
    void listPublished() throws Exception {
        when(catalog.listPublished())
                .thenReturn(
                        List.of(
                                new CatalogRow("notion", "Notion", "d", Map.of(), 1, Map.of())));

        var res = svc.list();

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().connectorKey()).isEqualTo("notion");
    }

    @Test
    void getNotFound() throws Exception {
        when(catalog.findPublished("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.get("missing")).isInstanceOf(McpApiException.class);
    }

    @Test
    void getByKey() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRow("notion", "Notion", "d", Map.of("f", 1), 2, Map.of())));

        var tool = svc.get("notion");

        assertThat(tool.policyPackVersion()).isEqualTo("2");
    }

    @Test
    void getWrapsSqlException() throws Exception {
        when(catalog.findPublished("notion")).thenThrow(new SQLException("db"));

        assertThatThrownBy(() -> svc.get("notion")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listWrapsSqlException() throws Exception {
        when(catalog.listPublished()).thenThrow(new SQLException("db"));

        assertThatThrownBy(() -> svc.list()).isInstanceOf(IllegalStateException.class);
    }
}
