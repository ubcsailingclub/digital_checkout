from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.schemas.member import MemberKioskResponse
from app.services.member_service import get_member_by_card_uid, normalize_card_uid
from app.services.wa_service import get_certifications_for_contact

router = APIRouter(prefix="/members", tags=["members"])


@router.get(
    "/card/{uid}",
    response_model=MemberKioskResponse,
    dependencies=[Depends(verify_kiosk_key)],
)
async def get_member_by_card(uid: str, db: Session = Depends(get_db)) -> MemberKioskResponse:
    """
    Look up a member by NFC card UID.
    Fetches WA certifications in the same request if WA is configured.
    """
    from app.models.member import Member, MemberCard
    from sqlalchemy import select

    # Resolve card → member to get wa_contact_id for WA lookup
    normalized = normalize_card_uid(uid)
    card = db.execute(
        select(MemberCard).where(
            MemberCard.card_uid_normalized == normalized,
            MemberCard.is_active == True,
        )
    ).scalar_one_or_none()

    certifications: list[str] = []
    if card:
        member = db.get(Member, card.member_id)
        if member and member.wa_contact_id:
            certifications = await get_certifications_for_contact(member.wa_contact_id)

    result = get_member_by_card_uid(db, uid, certifications)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not recognised or member inactive",
        )
    return result
