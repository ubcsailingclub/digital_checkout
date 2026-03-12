import logging

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.schemas.member import MemberKioskResponse, MemberListItem
from app.services.member_service import (
    get_all_active_members,
    get_member_by_card_uid,
    get_member_by_id,
)

router = APIRouter(prefix="/members", tags=["members"])

logger = logging.getLogger(__name__)


@router.get(
    "/list",
    response_model=list[MemberListItem],
    dependencies=[Depends(verify_kiosk_key)],
)
def list_members(db: Session = Depends(get_db)) -> list[MemberListItem]:
    """Return all active members sorted by name, for the kiosk name-search dropdown."""
    return get_all_active_members(db)


@router.get(
    "/by-id/{member_id}",
    response_model=MemberKioskResponse,
    dependencies=[Depends(verify_kiosk_key)],
)
def get_member_by_id_route(member_id: int, db: Session = Depends(get_db)) -> MemberKioskResponse:
    """Look up a member by their internal ID (used when selected from name dropdown)."""
    result = get_member_by_id(db, member_id)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Member not found or inactive",
        )
    return result


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
    result = get_member_by_card_uid(db, uid)
    if result is None:
        logger.warning("CARD NOT FOUND — raw: %r  normalized see member_service", uid)
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Card not recognised or member inactive",
        )
    return result
