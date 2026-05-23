"""MCP regress: authenticated catalog."""

import pytest
import requests

from lib.config import BFF_URL

pytestmark = pytest.mark.regress


def test_mcp_catalog_via_bff(api_session):
    r = requests.get(
        f"{BFF_URL}/api/mcp/catalog",
        headers=api_session.auth_headers(),
        timeout=30,
    )
    r.raise_for_status()
    items = r.json().get("items") or []
    assert any(i.get("connectorKey") for i in items)
