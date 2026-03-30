"""
Pull all members from Wild Apricot and upsert them into the local DB.

Populates:
  - members        (keyed by wa_contact_id)
  - member_cards   (keyed by normalized Jericho card number)

Run from the backend/ directory:
    python scripts/sync_members.py
"""

import base64
import json
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import httpx
from sqlalchemy import func, select
from sqlalchemy.orm import Session

# Allow `from app.xxx import ...`
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.config import settings
from app.db.session import engine
from app.models.member import Member, MemberCard
from app.services.member_service import normalize_card_uid
from app.services.wa_service import groups_to_certifications


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

WA_AUTH_URL = "https://oauth.wildapricot.org/auth/token"
WA_API_BASE = "https://api.wildapricot.org"
WA_API_VERSION = "v2.1"
PAGE_SIZE = 100

# Display name + stable system code for the WA custom field
JERICHO_CARD_FIELD = "Jericho Card Number"
JERICHO_CARD_SYSTEM_CODE = "custom-11866950"

# Only members with these WA membership-level names are treated as active kiosk users.
ALLOWED_MEMBERSHIP_LEVELS = {"UBC Student", "General Member"}


# ---------------------------------------------------------------------------
# WA API helpers
# ---------------------------------------------------------------------------

def _get_token(api_key: str) -> str:
    basic = base64.b64encode(f"APIKEY:{api_key}".encode()).decode("ascii")
    resp = httpx.post(
        WA_AUTH_URL,
        data={"grant_type": "client_credentials", "scope": "auto"},
        headers={"Authorization": f"Basic {basic}"},
        timeout=20,
    )
    resp.raise_for_status()
    token = resp.json().get("access_token")
    if not token:
        raise RuntimeError("WA token response missing access_token")
    return token


def _get_all_contacts(token: str, account_id: int) -> list[dict]:
    """
    Page through contacts from WA (not just active -- we set is_active from Status).

    The paged payload is used for bulk member upsert (fast), but some membership custom
    fields may be missing there, so card extraction can fall back to full contact fetch.
    """
    headers = {"Authorization": f"Bearer {token}"}
    contacts: list[dict] = []
    skip = 0

    while True:
        url = (
            f"{WA_API_BASE}/{WA_API_VERSION}/accounts/{account_id}/contacts"
            f"?$top={PAGE_SIZE}&$skip={skip}&$async=false"
            f"&$filter='Status' eq 'Active'"
            f"&$select=Id,FirstName,LastName,Email,Status,MembershipLevel,FieldValues"
        )
        resp = httpx.get(url, headers=headers, timeout=30)
        resp.raise_for_status()

        data = resp.json()
        batch = data.get("Contacts", [])
        contacts.extend(batch)

        print(f"  Fetched {len(contacts)} so far...")

        if len(batch) < PAGE_SIZE:
            break
        skip += PAGE_SIZE

    return contacts


def _get_contact_detail(token: str, account_id: int, contact_id: int) -> dict:
    """Fetch a single full contact record from WA."""
    headers = {"Authorization": f"Bearer {token}"}
    url = f"{WA_API_BASE}/{WA_API_VERSION}/accounts/{account_id}/contacts/{contact_id}"
    resp = httpx.get(url, headers=headers, timeout=30)
    resp.raise_for_status()
    return resp.json()


# ---------------------------------------------------------------------------
# Field extraction helpers
# ---------------------------------------------------------------------------

def _coerce_wa_field_value(val) -> str | None:
    """Convert a WA field value (string/int/list/dict) to a normalized string."""
    if val is None:
        return None

    if isinstance(val, str):
        v = val.strip()
        return v or None

    if isinstance(val, (int, float)):
        if isinstance(val, float) and val.is_integer():
            val = int(val)
        return str(val)

    if isinstance(val, list):
        parts: list[str] = []
        for item in val:
            if isinstance(item, dict):
                if item.get("Label") is not None:
                    parts.append(str(item["Label"]).strip())
                elif item.get("Value") is not None:
                    parts.append(str(item["Value"]).strip())
                else:
                    parts.append(str(item).strip())
            else:
                parts.append(str(item).strip())
        parts = [p for p in parts if p]
        return ", ".join(parts) if parts else None

    if isinstance(val, dict):
        for k in ("Label", "Value", "Name"):
            if val.get(k) is not None:
                v = str(val[k]).strip()
                return v or None
        v = str(val).strip()
        return v or None

    v = str(val).strip()
    return v or None


def _field_value_by_system_code(contact: dict, system_code: str) -> str | None:
    """Return a WA FieldValue by SystemCode as a normalized string, or None."""
    for fv in contact.get("FieldValues", []):
        if fv.get("SystemCode") == system_code:
            return _coerce_wa_field_value(fv.get("Value"))
    return None


def _field_value_by_name(contact: dict, field_name: str) -> str | None:
    """Return a WA FieldValue by display FieldName as a normalized string, or None."""
    for fv in contact.get("FieldValues", []):
        if fv.get("FieldName") == field_name:
            return _coerce_wa_field_value(fv.get("Value"))
    return None


def _field_value_by_name_or_system_code(
    contact: dict, *, field_name: str, system_code: str
) -> str | None:
    """
    Try system code first (stable), then field name (readable fallback).
    """
    val = _field_value_by_system_code(contact, system_code)
    if val:
        return val
    return _field_value_by_name(contact, field_name)


def _extract_groups(contact: dict) -> list[str]:
    """
    Extract MembershipLevel name + certification group labels from a WA contact.

    Certification groups live in the field with SystemCode='Groups'
    (display name: 'Equipment certification achieved').
    MembershipLevel is also included for Exec/Instructor catch-all mappings.
    """
    groups: list[str] = []
    level = contact.get("MembershipLevel")
    if isinstance(level, dict) and level.get("Name"):
        groups.append(level["Name"])
    for fv in contact.get("FieldValues", []):
        if fv.get("SystemCode") == "Groups" or fv.get("FieldName") == "Equipment certification achieved":
            val = fv.get("Value")
            if isinstance(val, list):
                groups.extend(item.get("Label", "") for item in val if item.get("Label"))
    return groups


# ---------------------------------------------------------------------------
# Rate-limited parallel individual-contact hydration
# ---------------------------------------------------------------------------

# WA allows 120 requests/minute. We target 100/min (0.6s between requests)
# to leave headroom. A global lock serialises the minimum-interval enforcement
# across all worker threads.
_rate_lock = threading.Lock()
_last_request_ts: float = 0.0
_MIN_INTERVAL = 0.6  # seconds -> ~100 req/min


def _get_contact_detail_throttled(token: str, account_id: int, contact_id: int) -> dict:
    global _last_request_ts
    with _rate_lock:
        elapsed = time.monotonic() - _last_request_ts
        if elapsed < _MIN_INTERVAL:
            time.sleep(_MIN_INTERVAL - elapsed)
        _last_request_ts = time.monotonic()
    return _get_contact_detail(token, account_id, contact_id)


def _prefetch_active_contacts(
    contacts: list[dict],
    token: str,
    account_id: int,
    max_workers: int = 4,
) -> dict[int, dict]:
    """
    Fetch full contact records from WA for all active members.

    Uses a global rate limiter (~100 req/min) to stay within WA's 120/min cap.
    With 4 workers and 0.6 s spacing the wall-clock time is roughly
    (active_count x 0.6 s) -- about 5–6 min for 500 active members.
    Returns a mapping of {wa_contact_id: full_contact_dict}.
    """
    active_ids = [
        c["Id"]
        for c in contacts
        if c.get("Id")
        and (c.get("Status") or "").strip().lower() == "active"
        and (c.get("MembershipLevel") or {}).get("Name", "").strip() in ALLOWED_MEMBERSHIP_LEVELS
    ]
    total = len(active_ids)
    eta_min = round(total * _MIN_INTERVAL / 60, 1)
    print(f"  Pre-fetching {total} active contacts (~{eta_min} min at {int(60/_MIN_INTERVAL)} req/min)...")

    results: dict[int, dict] = {}
    done = 0

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {
            pool.submit(_get_contact_detail_throttled, token, account_id, wa_id): wa_id
            for wa_id in active_ids
        }
        for future in as_completed(futures):
            wa_id = futures[future]
            done += 1
            if done % 50 == 0 or done == total:
                print(f"    ...fetched {done}/{total}")
            try:
                results[wa_id] = future.result()
            except Exception as e:
                print(f"  [WARN] Failed to fetch contact {wa_id}: {e}")

    print(f"  Pre-fetch complete: {len(results)}/{total} succeeded.\n")
    return results


# ---------------------------------------------------------------------------
# Sync logic
# ---------------------------------------------------------------------------

def _sync(
    session: Session,
    contacts: list[dict],
    token: str,
    account_id: int,
    full_contacts: dict[int, dict],
) -> None:
    members_upserted = 0
    cards_added = 0
    cards_deactivated = 0

    # SQLite + BIGINT PK migration workaround: manually assign IDs
    next_member_id = (session.execute(select(func.max(Member.id))).scalar() or 0) + 1
    next_card_id = (session.execute(select(func.max(MemberCard.id))).scalar() or 0) + 1

    total = len(contacts)

    for i, c in enumerate(contacts, start=1):
        if i % 50 == 0 or i == total:
            print(f"  Sync progress: {i}/{total}")
            session.commit()  # release write lock so checkouts can proceed

        wa_id = c.get("Id")
        if not wa_id:
            continue

        first = (c.get("FirstName") or "").strip()
        last = (c.get("LastName") or "").strip()
        full_name = f"{first} {last}".strip() or f"WA#{wa_id}"
        status = (c.get("Status") or "").strip()
        membership_level = (c.get("MembershipLevel") or {}).get("Name", "").strip()
        is_active = status.lower() == "active" and membership_level in ALLOWED_MEMBERSHIP_LEVELS

        # ---- upsert Member ----
        member = session.execute(
            select(Member).where(Member.wa_contact_id == wa_id)
        ).scalar_one_or_none()

        if member is None:
            member = Member(
                id=next_member_id,
                wa_contact_id=wa_id,
                full_name=full_name,
                first_name=first or None,
                last_name=last or None,
                membership_status=status,
                is_active=is_active,
            )
            session.add(member)
            session.flush()
            next_member_id += 1
        else:
            member.full_name = full_name
            member.first_name = first or None
            member.last_name = last or None
            member.membership_status = status
            member.is_active = is_active

        members_upserted += 1

        # ---- card + certifications -- WA is the source of truth ----
        # Use pre-fetched full contact for active members; fall back to paged data.
        c_full = full_contacts.get(wa_id)  # None for inactive members
        contact_rich = c_full if c_full is not None else c

        jericho_no = _field_value_by_name_or_system_code(
            contact_rich,
            field_name=JERICHO_CARD_FIELD,
            system_code=JERICHO_CARD_SYSTEM_CODE,
        )

        # Update certifications from the richer contact record
        groups = _extract_groups(contact_rich)
        certs = groups_to_certifications(groups)
        member.certifications_json = json.dumps(certs) if certs else None

        wa_normalized = normalize_card_uid(jericho_no) if jericho_no else None

        existing_cards = session.execute(
            select(MemberCard).where(MemberCard.member_id == member.id)
        ).scalars().all()

        if wa_normalized is None:
            # WA has no card on file -- deactivate all active cards for this member
            for card in existing_cards:
                if card.is_active:
                    card.is_active = False
                    cards_deactivated += 1
            continue

        already_correct = any(
            card.card_uid_normalized == wa_normalized and card.is_active
            for card in existing_cards
        )
        if already_correct:
            continue

        # Deactivate old active cards for this member
        for card in existing_cards:
            if card.is_active:
                card.is_active = False
                cards_deactivated += 1
                print(f"  [ROTATE] Deactivated old card {card.card_uid_normalized!r} for {full_name}")

        # Upsert target card globally (handle reassignment)
        target_card = session.execute(
            select(MemberCard).where(MemberCard.card_uid_normalized == wa_normalized)
        ).scalar_one_or_none()

        if target_card is not None:
            if target_card.member_id != member.id:
                print(
                    f"  [WARN] Card {wa_normalized!r} reassigned from "
                    f"member_id={target_card.member_id} -> {member.id} ({full_name})"
                )
            target_card.member_id = member.id
            target_card.is_active = True
            target_card.card_uid = jericho_no  # keep raw value in sync
        else:
            session.add(
                MemberCard(
                    id=next_card_id,
                    member_id=member.id,
                    card_uid=jericho_no,
                    card_uid_normalized=wa_normalized,
                    is_active=True,
                    label="Jericho Card",
                )
            )
            next_card_id += 1
            cards_added += 1

    # Deactivate any DB members not present in this sync — they've left or lapsed in WA.
    synced_wa_ids = {c["Id"] for c in contacts if c.get("Id")}
    stale = session.execute(
        select(Member).where(
            Member.wa_contact_id.notin_(synced_wa_ids),
            Member.is_active == True,
        )
    ).scalars().all()
    members_deactivated = 0
    for m in stale:
        m.is_active = False
        members_deactivated += 1
        print(f"  [DEACTIVATED] {m.full_name}")

    session.commit()
    print(
        f"\nDone. Members upserted: {members_upserted} | "
        f"Cards added: {cards_added} | Cards deactivated: {cards_deactivated} | "
        f"Members deactivated: {members_deactivated}"
    )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    if not settings.wa_api_key:
        print("ERROR: WA_API_KEY not set in backend/.env")
        sys.exit(1)
    if not settings.wa_account_id:
        print("ERROR: WA_ACCOUNT_ID not set in backend/.env")
        sys.exit(1)

    print("Authenticating with Wild Apricot...")
    token = _get_token(settings.wa_api_key)
    print("OK.\n")

    print("Fetching contacts from WA...")
    contacts = _get_all_contacts(token, settings.wa_account_id)
    print(f"Total contacts fetched: {len(contacts)}\n")

    print("Pre-fetching full records for active members...")
    full_contacts = _prefetch_active_contacts(contacts, token, settings.wa_account_id)

    print("Syncing to local DB...")
    with Session(engine) as session:
        _sync(session, contacts, token, settings.wa_account_id, full_contacts)


if __name__ == "__main__":
    main()