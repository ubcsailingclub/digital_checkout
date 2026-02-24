from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.craft import Craft
from app.models.checkout_session import CheckoutSession
from app.schemas.craft import CraftResponse


def get_crafts_with_availability(db: Session) -> list[CraftResponse]:
    """Return all active craft with is_available computed from active sessions."""
    # Subquery: count active sessions per craft
    active_sessions_sq = (
        select(CheckoutSession.craft_id, func.count().label("active_count"))
        .where(CheckoutSession.status == "active")
        .group_by(CheckoutSession.craft_id)
        .subquery()
    )

    rows = db.execute(
        select(Craft, active_sessions_sq.c.active_count)
        .outerjoin(active_sessions_sq, Craft.id == active_sessions_sq.c.craft_id)
        .where(Craft.is_active == True)
        .order_by(Craft.craft_class, Craft.display_name)
    ).all()

    return [
        CraftResponse(
            id           = craft.id,
            code         = craft.craft_code,
            display_name = craft.display_name,
            craft_class  = craft.craft_class,
            is_available = (craft.status == "available") and (active_count is None or active_count == 0),
        )
        for craft, active_count in rows
    ]
