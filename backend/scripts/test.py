import base64
import sys
from pathlib import Path
import json

import httpx

# Allow `from app.xxx import ...`
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.config import settings

WA_AUTH_URL = "https://oauth.wildapricot.org/auth/token"
WA_API_BASE = "https://api.wildapricot.org"
WA_API_VERSION = "v2.1"

# Put a real WA contact ID here (from your local members.wa_contact_id)
TEST_CONTACT_ID = 74801960  # <-- change this


def get_token(api_key: str) -> str:
    basic = base64.b64encode(f"APIKEY:{api_key}".encode()).decode("ascii")
    r = httpx.post(
        WA_AUTH_URL,
        data={"grant_type": "client_credentials", "scope": "auto"},
        headers={"Authorization": f"Basic {basic}"},
        timeout=20,
    )
    r.raise_for_status()
    return r.json()["access_token"]


def get_contact_detail(token: str, account_id: int, contact_id: int) -> dict:
    r = httpx.get(
        f"{WA_API_BASE}/{WA_API_VERSION}/accounts/{account_id}/contacts/{contact_id}",
        headers={"Authorization": f"Bearer {token}"},
        timeout=30,
    )
    r.raise_for_status()
    return r.json()


def main():
    token = get_token(settings.wa_api_key)
    contact = get_contact_detail(token, settings.wa_account_id, TEST_CONTACT_ID)

    print("Top-level keys:", list(contact.keys()))
    print("FieldValues count:", len(contact.get("FieldValues", [])))

    found = False
    for fv in contact.get("FieldValues", []):
        fname = str(fv.get("FieldName", ""))
        if "jericho" in fname.lower():
            found = True
            print("\n[Jericho-like field found]")
            print(json.dumps(fv, indent=2, default=str))

    if not found:
        print("\nNo Jericho-like field found in FieldValues.")
        print("First 20 FieldValues for inspection:")
        print(json.dumps(contact.get("FieldValues", [])[:20], indent=2, default=str))


if __name__ == "__main__":
    main()