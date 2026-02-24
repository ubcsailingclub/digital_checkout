"""
Pull all active members from Wild Apricot and upsert them into the local DB.

Populates:
  - members        (keyed by wa_contact_id)
  - member_cards   (keyed by normalised "Jericho Card Number" custom field)

Run from the backend/ directory:
    python scripts/sync_members.py
"""

import base64
import sys
from pathlib import Path

import httpx
from sqlalchemy import func, select
from sqlalchemy.orm import Session

# Allow `from app.xxx import ...`
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.config import settings
from app.db.session import engine
from app.models.member import Member, MemberCard

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

WA_AUTH_URL    = "https://oauth.wildapricot.org/auth/token"
WA_API_BASE    = "https://api.wildapricot.org"
WA_API_VERSION = "v2.1"
PAGE_SIZE      = 100

JERICHO_CARD_FIELD = "Jericho Card Number"

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
    """Page through all contacts from WA (not just active — we set is_active from Status)."""
    headers  = {"Authorization": f"Bearer {token}"}
    contacts = []
    skip     = 0

    while True:
        url = (
            f"{WA_API_BASE}/{WA_API_VERSION}/accounts/{account_id}/contacts"
            f"?$top={PAGE_SIZE}&$skip={skip}&$async=false"
            f"&$select=Id,FirstName,LastName,Email,Status,MembershipLevel,FieldValues"
        )
        resp = httpx.get(url, headers=headers, timeout=30)
        resp.raise_for_status()
        data  = resp.json()
        batch = data.get("Contacts", [])
        contacts.extend(batch)
        print(f"  Fetched {len(contacts)} so far…")
        if len(batch) < PAGE_SIZE:
            break
        skip += PAGE_SIZE

    return contacts


# ---------------------------------------------------------------------------
# Field extraction helpers
# ---------------------------------------------------------------------------

def _field_value(contact: dict, field_name: str) -> str | None:
    """Return the string value of a WA custom FieldValue by name, or None."""
    for fv in contact.get("FieldValues", []):
        if fv.get("FieldName") == field_name:
            val = fv.get("Value")
            if isinstance(val, str) and val.strip():
                return val.strip()
    return None


def _extract_groups(contact: dict) -> list[str]:
    """Return MembershipLevel name + all Group participation labels."""
    groups: list[str] = []

    level = contact.get("MembershipLevel")
    if isinstance(level, dict) and level.get("Name"):
        groups.append(level["Name"])

    for fv in contact.get("FieldValues", []):
        if fv.get("FieldName") == "Group participation":
            val = fv.get("Value")
            if isinstance(val, list):
                groups.extend(item.get("Label", "") for item in val if item.get("Label"))

    return groups


def _normalize_card(uid: str) -> str:
    return uid.strip().upper()


# ---------------------------------------------------------------------------
# Sync logic
# ---------------------------------------------------------------------------

def _sync(session: Session, contacts: list[dict]) -> None:
    members_upserted = 0
    cards_added      = 0
    cards_deactivated = 0

    # SQLite doesn't auto-generate IDs unless the column is declared as
    # INTEGER PRIMARY KEY inline — Alembic migrations use BIGINT which breaks
    # autoincrement. Work around by tracking IDs manually.
    next_member_id = (session.execute(select(func.max(Member.id))).scalar() or 0) + 1
    next_card_id   = (session.execute(select(func.max(MemberCard.id))).scalar() or 0) + 1

    for c in contacts:
        wa_id = c.get("Id")
        if not wa_id:
            continue

        first     = (c.get("FirstName") or "").strip()
        last      = (c.get("LastName")  or "").strip()
        full_name = f"{first} {last}".strip() or f"WA#{wa_id}"
        email     = (c.get("Email") or "").strip() or None
        status    = (c.get("Status") or "").strip()
        is_active = status.lower() == "active"

        # ---- upsert Member ----
        member = session.execute(
            select(Member).where(Member.wa_contact_id == wa_id)
        ).scalar_one_or_none()

        if member is None:
            member = Member(
                id                = next_member_id,
                wa_contact_id     = wa_id,
                full_name         = full_name,
                first_name        = first or None,
                last_name         = last  or None,
                email             = email,
                membership_status = status,
                is_active         = is_active,
            )
            session.add(member)
            session.flush()
            next_member_id += 1
        else:
            member.full_name         = full_name
            member.first_name        = first or None
            member.last_name         = last  or None
            member.email             = email
            member.membership_status = status
            member.is_active         = is_active

        members_upserted += 1

        # ---- card rotation — WA is the source of truth ----
        # Get what WA says the card number should be right now
        jericho_no = _field_value(c, JERICHO_CARD_FIELD)
        wa_normalized = _normalize_card(jericho_no) if jericho_no else None

        # All existing cards for this member
        existing_cards = session.execute(
            select(MemberCard).where(MemberCard.member_id == member.id)
        ).scalars().all()

        if wa_normalized is None:
            # WA has no card on file — deactivate everything
            for card in existing_cards:
                if card.is_active:
                    card.is_active = False
                    cards_deactivated += 1
            continue

        # Check if the correct card is already active
        already_correct = any(
            card.card_uid_normalized == wa_normalized and card.is_active
            for card in existing_cards
        )

        if not already_correct:
            # Deactivate all old cards for this member
            for card in existing_cards:
                if card.is_active:
                    card.is_active = False
                    cards_deactivated += 1
                    print(f"  [ROTATE] Deactivated old card {card.card_uid_normalized!r} for {full_name}")

            # Does the new card already exist in the DB (possibly for another member)?
            target_card = session.execute(
                select(MemberCard).where(MemberCard.card_uid_normalized == wa_normalized)
            ).scalar_one_or_none()

            if target_card is not None:
                if target_card.member_id != member.id:
                    print(
                        f"  [WARN] Card {wa_normalized!r} reassigned from "
                        f"member_id={target_card.member_id} → {member.id} ({full_name})"
                    )
                target_card.member_id = member.id
                target_card.is_active = True
            else:
                session.add(MemberCard(
                    id                  = next_card_id,
                    member_id           = member.id,
                    card_uid            = jericho_no,
                    card_uid_normalized = wa_normalized,
                    is_active           = True,
                    label               = "Jericho Card",
                ))
                next_card_id += 1
                cards_added += 1

    session.commit()
    print(
        f"\nDone. Members upserted: {members_upserted} | "
        f"Cards added: {cards_added} | Cards deactivated: {cards_deactivated}"
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

    print("Authenticating with Wild Apricot…")
    token = _get_token(settings.wa_api_key)
    print("OK.\n")

    print("Fetching contacts from WA…")
    contacts = _get_all_contacts(token, settings.wa_account_id)
    print(f"Total contacts fetched: {len(contacts)}\n")

    print("Syncing to local DB…")
    with Session(engine) as session:
        _sync(session, contacts)


if __name__ == "__main__":
    main()
