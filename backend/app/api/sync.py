"""
Sync endpoints — called by the tablet's WorkManager hourly.

GET /sync/members  → full member + card export (kiosk key required)
GET /sync/fleet    → craft list with current status (kiosk key required)
"""

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.models.craft import Craft
from app.models.member import Member, MemberCard

router = APIRouter(prefix="/sync", tags=["sync"])


@router.get("/members", dependencies=[Depends(verify_kiosk_key)])
def sync_members(db: Session = Depends(get_db)) -> dict:
    """Export all active members with their NFC cards and certifications."""
    members = db.execute(
        select(Member).where(Member.is_active == True)
    ).scalars().all()

    payload = []
    for m in members:
        cards = db.execute(
            select(MemberCard).where(
                MemberCard.member_id == m.id,
                MemberCard.is_active == True
            )
        ).scalars().all()

        payload.append({
            "id":                  m.id,
            "wa_contact_id":       m.wa_contact_id,
            "full_name":           m.full_name,
            "first_name":          m.first_name,
            "last_name":           m.last_name,
            "membership_status":   m.membership_status,
            "is_active":           m.is_active,
            "certifications_json": m.certifications_json,
            "cards": [
                {"card_uid_normalized": c.card_uid_normalized, "is_active": c.is_active}
                for c in cards
            ],
        })

    return {"members": payload}


@router.get("/fleet", dependencies=[Depends(verify_kiosk_key)])
def sync_fleet(db: Session = Depends(get_db)) -> dict:
    """Export all active craft with their current grounding status."""
    crafts = db.execute(
        select(Craft).where(Craft.is_active == True)
        .order_by(Craft.fleet_type, Craft.display_name)
    ).scalars().all()

    return {
        "craft": [
            {
                "id":               c.id,
                "craft_code":       c.craft_code,
                "display_name":     c.display_name,
                "fleet_type":       c.fleet_type,
                "craft_class":      c.craft_class,
                "capacity":         c.capacity,
                "is_active":        c.is_active,
                "requires_checkout": c.requires_checkout,
                "status":           c.status,
                "status_reason":    c.status_reason,
            }
            for c in crafts
        ]
    }
