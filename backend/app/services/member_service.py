import re

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.member import Member, MemberCard
from app.models.checkout_session import CheckoutSession
from app.models.craft import Craft
from app.schemas.member import ActiveCheckoutInfo, MemberKioskResponse


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

    # Check for an active checkout
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
