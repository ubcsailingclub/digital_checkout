from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.schemas.craft import CraftResponse
from app.services.craft_service import get_crafts_with_availability

router = APIRouter(prefix="/crafts", tags=["crafts"])


@router.get("", response_model=list[CraftResponse], dependencies=[Depends(verify_kiosk_key)])
def list_crafts(db: Session = Depends(get_db)) -> list[CraftResponse]:
    """Return all active craft with real-time availability."""
    return get_crafts_with_availability(db)
