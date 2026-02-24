from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.schemas.member import MemberKioskResponse
from app.services.member_service import get_member_by_card_uid

router = APIRouter(prefix="/members", tags=["members"])


@router.get(
    "/card/{uid}",
    response_model=MemberKioskResponse,
    dependencies=[Depends(verify_kiosk_key)],
)
def get_member_by_card(uid: str, db: Session = Depends(get_db)) -> MemberKioskResponse:
    """
    Look up a member by NFC card UID.
    Certifications are read from the local DB (populated during WA sync).
    """
    import logging
    result = get_member_by_card_uid(db, uid)
    if result is None:
        logging.getLogger(__name__).warning(
            "CARD NOT FOUND — raw: %r  normalized see member_service", uid
        )
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not recognised or member inactive",
        )
    return result
