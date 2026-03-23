import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.checkout_session import CheckoutSession
from app.models.craft import Craft
from app.models.crew import SessionCrewMember
from app.models.member import Member, MemberCard
from app.schemas.session import ActiveSessionInfo, CheckinRequest, CrewInput, RecentSessionInfo, SessionCreate, SessionResponse
from app.services.member_service import normalize_card_uid
from app.services.sheets_service import post_checkout_event, post_damage_report

logger = logging.getLogger(__name__)


def _resolve_member_id(db: Session, card_uid: str, member_id: int | None) -> int:
    """Return the DB member_id from either a direct member_id or a card UID lookup."""
    if member_id is not None:
        member = db.get(Member, member_id)
        if member is None or not member.is_active:
            raise ValueError("Member not found")
        return member_id
    uid = normalize_card_uid(card_uid)
    card = db.execute(
        select(MemberCard).where(
            MemberCard.card_uid_normalized == uid, MemberCard.is_active == True
        )
    ).scalar_one_or_none()
    if card is None:
        raise ValueError("Card not recognised")
    return card.member_id


def create_checkout(db: Session, req: SessionCreate) -> SessionResponse:
    resolved_member_id = _resolve_member_id(db, req.card_uid, req.member_id)

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
        member_id            = resolved_member_id,
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
        crew_member_id: int | None = entry.member_id
        if crew_member_id is None and entry.card_uid:
            crew_uid = normalize_card_uid(entry.card_uid)
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

    # Fire-and-forget: log checkout to Google Sheet
    member_obj = db.get(Member, resolved_member_id)
    post_checkout_event(
        event_type           = "checkout",
        member_name          = member_obj.full_name if member_obj else "Unknown",
        craft_name           = craft.display_name,
        craft_code           = craft.craft_code,
        session_id           = session.id,
        party_size           = session.party_size,
        expected_return_time = session.expected_return_time,
        checkout_time        = session.checkout_time,
    )

    return SessionResponse(
        session_id = session.id,
        status     = "active",
        message    = f"Checked out {craft.display_name}",
    )


def complete_checkin(db: Session, req: CheckinRequest, session_id: int) -> SessionResponse:
    """Check in a session by its ID.

    Any valid member card may check in any session — not just the original skipper's.
    This allows crew members or volunteers to return a boat without the skipper present.
    """
    resolved_member_id = _resolve_member_id(db, req.card_uid, req.member_id)

    session = db.execute(
        select(CheckoutSession).where(
            CheckoutSession.id == session_id,
            CheckoutSession.status == "active",
        )
    ).scalar_one_or_none()

    if session is None:
        raise ValueError("Session not found or already returned")

    craft = db.get(Craft, session.craft_id)

    # Use the skipper's member record for the damage report name
    skipper = db.get(Member, session.member_id)

    now = datetime.now(tz=timezone.utc)
    session.checkin_time    = now
    session.status          = "completed"
    session.checkin_method  = "self_service"
    session.checkin_actor   = str(resolved_member_id)   # who performed the check-in
    session.notes_in        = req.notes_in or None
    session.damage_reported = req.damage_reported
    db.commit()

    skipper_name = skipper.full_name if skipper else "Unknown"
    craft_name   = craft.display_name if craft else "Unknown craft"
    craft_code   = craft.craft_code if craft else ""

    # Fire-and-forget: post to Google Sheet if damage was reported
    if req.damage_reported:
        post_damage_report(
            member_name = skipper_name,
            craft_name  = craft_name,
            notes       = req.notes_in,
            session_id  = session.id,
        )

    # Fire-and-forget: log check-in to the checkout log sheet
    post_checkout_event(
        event_type       = "checkin",
        member_name      = skipper_name,
        craft_name       = craft_name,
        craft_code       = craft_code,
        session_id       = session.id,
        party_size       = session.party_size,
        checkout_time    = session.checkout_time,
        checkin_time     = session.checkin_time,
        notes            = session.notes_in,
        damage_reported  = session.damage_reported,
    )

    return SessionResponse(
        session_id = session.id,
        status     = "completed",
        message    = f"{craft_name} returned",
    )


def list_active_sessions(db: Session) -> list[ActiveSessionInfo]:
    """Return all currently active checkout sessions with craft and member info."""
    rows = db.execute(
        select(CheckoutSession, Craft, Member)
        .join(Craft, CheckoutSession.craft_id == Craft.id)
        .join(Member, CheckoutSession.member_id == Member.id)
        .where(CheckoutSession.status == "active")
        .order_by(CheckoutSession.checkout_time)
    ).all()

    return [
        ActiveSessionInfo(
            session_id           = session.id,
            craft_id             = session.craft_id,
            craft_code           = craft.craft_code,
            craft_name           = craft.display_name,
            member_name          = member.full_name,
            checkout_time        = session.checkout_time,
            expected_return_time = session.expected_return_time,
        )
        for session, craft, member in rows
    ]


def list_recent_sessions(db: Session, limit: int = 7) -> list[RecentSessionInfo]:
    """Return the most recent sessions (any status) for the idle-screen logbook."""
    rows = db.execute(
        select(CheckoutSession, Craft, Member)
        .join(Craft, CheckoutSession.craft_id == Craft.id)
        .join(Member, CheckoutSession.member_id == Member.id)
        .order_by(CheckoutSession.checkout_time.desc())
        .limit(limit)
    ).all()

    if not rows:
        return []

    session_ids = [session.id for session, _, _ in rows]

    crew_rows = db.execute(
        select(SessionCrewMember)
        .where(SessionCrewMember.session_id.in_(session_ids))
    ).scalars().all()

    crew_by_session: dict[int, list[str]] = {}
    for crew in crew_rows:
        crew_by_session.setdefault(crew.session_id, []).append(crew.display_name)

    return [
        RecentSessionInfo(
            session_id           = session.id,
            skipper_name         = member.full_name,
            crew_names           = crew_by_session.get(session.id, []),
            craft_name           = craft.display_name,
            craft_code           = craft.craft_code,
            checkout_time        = session.checkout_time,
            expected_return_time = session.expected_return_time,
            checkin_time         = session.checkin_time,
            status               = session.status,
        )
        for session, craft, member in rows
    ]


def auto_expire_overdue_sessions(db: Session, grace_hours: int = 2) -> int:
    """Mark sessions as expired when they are past ETR by more than grace_hours.

    Returns the number of sessions closed.
    """
    cutoff = datetime.now(tz=timezone.utc) - timedelta(hours=grace_hours)

    rows = db.execute(
        select(CheckoutSession, Craft, Member)
        .join(Craft,  CheckoutSession.craft_id  == Craft.id)
        .join(Member, CheckoutSession.member_id == Member.id)
        .where(
            CheckoutSession.status == "active",
            CheckoutSession.expected_return_time != None,
            CheckoutSession.expected_return_time < cutoff,
        )
    ).all()

    now = datetime.now(tz=timezone.utc)
    for session, craft, member in rows:
        session.status         = "completed"
        session.checkin_time   = now
        session.checkin_method = "auto_expired"
        logger.info(
            "Auto-expired session %d (craft=%s, ETR was %s)",
            session.id, craft.craft_code, session.expected_return_time,
        )

    if rows:
        db.commit()

    # Fire-and-forget: log each auto-expiry to the checkout log sheet
    for session, craft, member in rows:
        post_checkout_event(
            event_type    = "auto_expired",
            member_name   = member.full_name,
            craft_name    = craft.display_name,
            craft_code    = craft.craft_code,
            session_id    = session.id,
            party_size    = session.party_size,
            checkout_time = session.checkout_time,
            checkin_time  = session.checkin_time,
        )

    return len(rows)
