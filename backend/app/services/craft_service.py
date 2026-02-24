from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.craft import Craft
from app.models.checkout_session import CheckoutSession
from app.schemas.craft import CraftResponse


def get_crafts_with_availability(db: Session) -> list[CraftResponse]:
    """Return all active craft with is_available and expected_return_time from active sessions."""
    # Subquery: aggregate active session info per craft (count + earliest ETR)
    active_sessions_sq = (
        select(
            CheckoutSession.craft_id,
            func.count().label("active_count"),
            func.min(CheckoutSession.expected_return_time).label("expected_return_time"),
        )
        .where(CheckoutSession.status == "active")
        .group_by(CheckoutSession.craft_id)
        .subquery()
    )

    rows = db.execute(
        select(
            Craft,
            active_sessions_sq.c.active_count,
            active_sessions_sq.c.expected_return_time,
        )
        .outerjoin(active_sessions_sq, Craft.id == active_sessions_sq.c.craft_id)
        .where(Craft.is_active == True)
        .order_by(Craft.craft_class, Craft.display_name)
    ).all()

    return [
        CraftResponse(
            id                   = craft.id,
            code                 = craft.craft_code,
            display_name         = craft.display_name,
            craft_class          = craft.craft_class,
            is_available         = (craft.status == "available") and (active_count is None or active_count == 0),
            # Only expose ETR when the craft is actually checked out
            expected_return_time = expected_return_time if (active_count is not None and active_count > 0) else None,
        )
        for craft, active_count, expected_return_time in rows
    ]
