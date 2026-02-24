"""
Wild Apricot service — ported from BlackbeardBot/cogs/verify.py.
Uses API key auth: Basic base64("APIKEY:{key}").
Uses httpx.AsyncClient instead of aiohttp.
"""

import base64
import datetime as dt
import json
from pathlib import Path
from typing import Any

import httpx

from app.core.config import settings

# ---------------------------------------------------------------------------
# Cert map — loaded once at module import
# ---------------------------------------------------------------------------

_CERT_MAP_PATH = Path(__file__).parent.parent / "core" / "cert_map.json"

def _load_cert_map() -> dict[str, list[str]]:
    try:
        return json.loads(_CERT_MAP_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}

_CERT_MAP: dict[str, list[str]] = _load_cert_map()


def groups_to_certifications(group_names: list[str]) -> list[str]:
    """
    Map a member's WA group names to the fleet classes they are certified for.
    '*' means all-access (Exec, Instructors).
    Returns sorted, deduplicated list of fleet class strings.
    """
    certified: set[str] = set()
    for group in group_names:
        permitted = _CERT_MAP.get(group, [])
        if "*" in permitted:
            return ["*"]  # all-access
        certified.update(permitted)
    return sorted(certified)


# ---------------------------------------------------------------------------
# WildApricotClient
# ---------------------------------------------------------------------------

class WildApricotClient:
    AUTH_URL = "https://oauth.wildapricot.org/auth/token"
    API_BASE = "https://api.wildapricot.org"

    def __init__(self, api_key: str, account_id: int, api_version: str = "v2.1") -> None:
        if not api_key:
            raise ValueError("WA_API_KEY is empty")
        if not account_id:
            raise ValueError("WA_ACCOUNT_ID is empty/0")

        self.api_key     = api_key
        self.account_id  = account_id
        self.api_version = api_version

        self._token: str | None = None
        self._token_expiry: dt.datetime | None = None
        self._client: httpx.AsyncClient | None = None

    async def start(self) -> None:
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=20.0)

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _ensure_token(self) -> str:
        now = dt.datetime.now(tz=dt.timezone.utc)
        if self._token and self._token_expiry:
            if now < (self._token_expiry - dt.timedelta(seconds=30)):
                return self._token

        if self._client is None:
            raise RuntimeError("WildApricotClient not started")

        basic = base64.b64encode(f"APIKEY:{self.api_key}".encode()).decode("ascii")
        resp = await self._client.post(
            self.AUTH_URL,
            data={"grant_type": "client_credentials", "scope": "auto"},
            headers={"Authorization": f"Basic {basic}"},
        )
        if resp.status_code != 200:
            raise RuntimeError(f"WA token request failed: HTTP {resp.status_code}")

        payload = resp.json()
        token = payload.get("access_token")
        if not token:
            raise RuntimeError("WA token response missing access_token")

        expires_in = int(payload.get("expires_in", 3600))
        self._token = token
        self._token_expiry = now + dt.timedelta(seconds=expires_in)
        return token

    async def get_contact(self, contact_id: int) -> dict[str, Any]:
        if self._client is None:
            raise RuntimeError("WildApricotClient not started")

        token = await self._ensure_token()
        url = f"{self.API_BASE}/{self.api_version}/accounts/{self.account_id}/contacts/{contact_id}"
        resp = await self._client.get(url, headers={"Authorization": f"Bearer {token}"})

        if resp.status_code == 404:
            return {}
        if resp.status_code != 200:
            raise RuntimeError(f"WA contact lookup failed: HTTP {resp.status_code}")
        return resp.json()

    async def get_member_group_names(self, wa_contact_id: int) -> list[str]:
        """Fetch WA contact and return list of group/level names."""
        contact = await self.get_contact(wa_contact_id)
        groups: list[str] = []

        # MembershipLevel
        level = contact.get("MembershipLevel")
        if isinstance(level, dict) and level.get("Name"):
            groups.append(level["Name"])

        # FieldValues — certification groups (SystemCode='Groups',
        # display name 'Equipment certification achieved')
        for field in contact.get("FieldValues", []):
            if field.get("SystemCode") == "Groups" or field.get("FieldName") == "Equipment certification achieved":
                value = field.get("Value")
                if isinstance(value, list):
                    groups.extend(
                        item.get("Label", "") for item in value if item.get("Label")
                    )

        return groups


# ---------------------------------------------------------------------------
# Module-level singleton (initialised lazily when settings are available)
# ---------------------------------------------------------------------------

_wa_client: WildApricotClient | None = None


def get_wa_client() -> WildApricotClient | None:
    """Return the shared client, or None if WA credentials are not configured."""
    global _wa_client
    if _wa_client is None and settings.wa_api_key and settings.wa_account_id:
        _wa_client = WildApricotClient(settings.wa_api_key, settings.wa_account_id)
    return _wa_client


async def get_certifications_for_contact(wa_contact_id: int) -> list[str]:
    """
    High-level helper: start client if needed, fetch group names, map to certs.
    Returns [] if WA is not configured or the lookup fails.
    """
    client = get_wa_client()
    if client is None:
        return []
    try:
        if client._client is None:
            await client.start()
        groups = await client.get_member_group_names(wa_contact_id)
        return groups_to_certifications(groups)
    except Exception:
        return []
