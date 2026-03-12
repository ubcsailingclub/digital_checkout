import re

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.member import Member, MemberCard
from app.models.checkout_session import CheckoutSession
from app.models.craft import Craft
from app.schemas.member import ActiveCheckoutInfo, MemberKioskResponse, MemberListItem


def normalize_card_uid(uid: str) -> str:
    """
    Return an uppercase hex string with no separators.

    Handles two formats:
    - Hex (from Android NFC hardware UID): "04:A3:B2:C1" or "04A3B2C1" → "04A3B2C1"
    - Decimal (from Wild Apricot "Jericho Card Number"): "61699" → "F103"

    Some readers prepend extra facility-code digits to the decimal card number
    (e.g. "0906100061699" for a card stored in WA as "61699"). We always keep
    only the last 5 decimal digits before converting, which covers all valid
    Jericho card numbers (max 99999).
    """
    cleaned = re.sub(r"[\s:]", "", uid).upper()
    if re.fullmatch(r"[0-9]+", cleaned):
        # Pure decimal string — take the last 5 digits to strip any prepended
        # facility-code bytes, then convert to hex.
        card_digits = cleaned[-5:]
        return format(int(card_digits), "X")
    # Hex string — strip leading zeros so "0000F103" == "F103" == decimal 61699
    return cleaned.lstrip("0") or "0"


def _build_display_name(member: Member) -> str:
    """Return 'First L.' to minimise PII on device."""
    if member.first_name and member.last_name:
        return f"{member.first_name} {member.last_name[0]}."
    if member.first_name:
        return member.first_name
    # Fallback: first word of full_name only
    return member.full_name.split()[0] if member.full_name else "Member"


def _build_kiosk_response(db: Session, member: Member) -> MemberKioskResponse:
    """Build a MemberKioskResponse from an already-loaded Member ORM object."""
    active_session = db.execute(
        select(CheckoutSession, Craft)
        .join(Craft, CheckoutSession.craft_id == Craft.id)
        .where(
            CheckoutSession.member_id == member.id,
            CheckoutSession.status == "active",
        )
    ).first()

    active_checkout_info: ActiveCheckoutInfo | None = None
    if active_session:
        session, craft = active_session
        active_checkout_info = ActiveCheckoutInfo(
            session_id = session.id,
            craft_code = craft.craft_code,
            craft_name = craft.display_name,
        )

    return MemberKioskResponse(
        id                  = member.id,
        display_name        = _build_display_name(member),
        has_active_checkout = active_checkout_info is not None,
        active_checkout     = active_checkout_info,
        certifications      = member.certifications,
    )


def get_member_by_card_uid(
    db: Session,
    raw_uid: str,
) -> MemberKioskResponse | None:
    """
    Look up a member by NFC card UID.
    Certifications are read from the member record (populated during WA sync).
    Returns None if card not found or member is inactive.
    """
    uid = normalize_card_uid(raw_uid)

    card = db.execute(
        select(MemberCard)
        .where(MemberCard.card_uid_normalized == uid, MemberCard.is_active == True)
    ).scalar_one_or_none()

    if card is None:
        return None

    member = db.get(Member, card.member_id)
    if member is None or not member.is_active:
        return None

    return _build_kiosk_response(db, member)


def get_member_by_id(db: Session, member_id: int) -> MemberKioskResponse | None:
    """Look up a member by their internal DB ID (used when selected from name dropdown)."""
    member = db.get(Member, member_id)
    if member is None or not member.is_active:
        return None
    return _build_kiosk_response(db, member)


def get_all_active_members(db: Session) -> list[MemberListItem]:
    """Return all active members sorted by name, for the kiosk name-search dropdown."""
    members = db.execute(
        select(Member)
        .where(Member.is_active == True)
        .order_by(Member.last_name, Member.first_name, Member.full_name)
    ).scalars().all()
    return [
        MemberListItem(id=m.id, display_name=m.full_name)
        for m in members
    ]
