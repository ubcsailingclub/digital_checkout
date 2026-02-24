from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.checkout_session import CheckoutSession
from app.models.craft import Craft
from app.models.crew import SessionCrewMember
from app.models.member import Member, MemberCard
from app.schemas.session import CheckinRequest, CrewInput, SessionCreate, SessionResponse
from app.services.member_service import normalize_card_uid
from app.services.sheets_service import post_damage_report


def create_checkout(db: Session, req: SessionCreate) -> SessionResponse:
    uid = normalize_card_uid(req.card_uid)

    card = db.execute(
        select(MemberCard).where(
            MemberCard.card_uid_normalized == uid, MemberCard.is_active == True
        )
    ).scalar_one_or_none()

    if card is None:
        raise ValueError("Card not recognised")

    craft = db.get(Craft, req.craft_id)
    if craft is None or not craft.is_active:
        raise ValueError("Craft not found")

    # Verify craft is available (no active session)
    existing = db.execute(
        select(CheckoutSession).where(
            CheckoutSession.craft_id == req.craft_id,
            CheckoutSession.status == "active",
        )
    ).scalar_one_or_none()
    if existing is not None:
        raise ValueError(f"{craft.display_name} is already checked out")

    now = datetime.now(tz=timezone.utc)
    expected_return = (
        now + timedelta(hours=req.expected_return_hours)
        if req.expected_return_hours
        else None
    )

    session = CheckoutSession(
        member_id            = card.member_id,
        craft_id             = req.craft_id,
        checkout_time        = now,
        status               = "active",
        expected_return_time = expected_return,
        party_size           = 1 + len(req.crew),
        checkout_method      = "self_service",
    )
    db.add(session)
    db.flush()  # get session.id before adding crew

    for entry in req.crew:
        crew_uid = normalize_card_uid(entry.card_uid) if entry.card_uid else None
        crew_member_id: int | None = None
        if crew_uid:
            crew_card = db.execute(
                select(MemberCard).where(MemberCard.card_uid_normalized == crew_uid)
            ).scalar_one_or_none()
            if crew_card:
                crew_member_id = crew_card.member_id

        db.add(
            SessionCrewMember(
                session_id   = session.id,
                member_id    = crew_member_id,
                display_name = entry.name,
                is_guest     = entry.is_guest,
            )
        )

    db.commit()
    db.refresh(session)

    return SessionResponse(
        session_id = session.id,
        status     = "active",
        message    = f"Checked out {craft.display_name}",
    )


def complete_checkin(db: Session, req: CheckinRequest) -> SessionResponse:
    uid = normalize_card_uid(req.card_uid)

    card = db.execute(
        select(MemberCard).where(
            MemberCard.card_uid_normalized == uid, MemberCard.is_active == True
        )
    ).scalar_one_or_none()

    if card is None:
        raise ValueError("Card not recognised")

    session = db.execute(
        select(CheckoutSession).where(
            CheckoutSession.member_id == card.member_id,
            CheckoutSession.status == "active",
        )
    ).scalar_one_or_none()

    if session is None:
        raise ValueError("No active checkout found for this card")

    craft = db.get(Craft, session.craft_id)

    member = db.get(Member, card.member_id)

    now = datetime.now(tz=timezone.utc)
    session.checkin_time    = now
    session.status          = "completed"
    session.checkin_method  = "self_service"
    session.notes_in        = req.notes_in or None
    session.damage_reported = req.damage_reported
    db.commit()

    # Fire-and-forget: post to Google Sheet if damage was reported
    if req.damage_reported:
        post_damage_report(
            member_name = member.full_name if member else "Unknown",
            craft_name  = craft.display_name if craft else "Unknown craft",
            notes       = req.notes_in,
            session_id  = session.id,
        )

    return SessionResponse(
        session_id = session.id,
        status     = "completed",
        message    = f"{craft.display_name if craft else 'Craft'} returned",
    )
