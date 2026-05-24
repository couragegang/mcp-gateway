package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.couragegang.mcp.integration.NotionApiClient;
import com.couragegang.mcp.integration.NotionApiClient.NotionTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotionDiscoverServiceTest {

    @Mock
    NotionApiClient notion;

    @InjectMocks
    NotionDiscoverService svc;

    @Test
    void mapsTargets() throws Exception {
        when(notion.listWritableTargets("tok"))
                .thenReturn(List.of(new NotionTarget("id1", "DB", "https://n", "database")));
        var res = svc.discover("tok");
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().id()).isEqualTo("id1");
        assertThat(res.items().getFirst().title()).isEqualTo("DB");
    }

    @Test
    void wrapsFailures() throws Exception {
        when(notion.listWritableTargets("bad")).thenThrow(new IllegalStateException("api down"));
        assertThatThrownBy(() -> svc.discover("bad"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Notion discover failed");
    }
}
