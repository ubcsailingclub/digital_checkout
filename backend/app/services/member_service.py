import re

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.member import Member, MemberCard
from app.models.checkout_session import CheckoutSession
from app.models.craft import Craft
from app.schemas.member import ActiveCheckoutInfo, MemberKioskResponse


def normalize_card_uid(uid: str) -> str:
    """Uppercase hex, strip all whitespace/colons."""
    return re.sub(r"[\s:]", "", uid).upper()


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
    certifications: list[str],
) -> MemberKioskResponse | None:
    """
    Look up a member by NFC card UID.
    `certifications` is the pre-resolved list from WA (or [] if WA unavailable).
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
        certifications      = certifications,
    )
