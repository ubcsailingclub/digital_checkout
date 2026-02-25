from fastapi import APIRouter, Depends, HTTPException, Path, status
from sqlalchemy.orm import Session

from app.api.deps import verify_kiosk_key
from app.db.deps import get_db
from app.schemas.session import ActiveSessionInfo, CheckinRequest, SessionCreate, SessionResponse
from app.services.session_service import complete_checkin, create_checkout, list_active_sessions

router = APIRouter(prefix="/sessions", tags=["sessions"])


@router.post(
    "",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(verify_kiosk_key)],
)
def checkout(req: SessionCreate, db: Session = Depends(get_db)) -> SessionResponse:
    """Create a new checkout session."""
    try:
        return create_checkout(db, req)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))


@router.get(
    "/active",
    response_model=list[ActiveSessionInfo],
    dependencies=[Depends(verify_kiosk_key)],
)
def get_active_sessions(db: Session = Depends(get_db)) -> list[ActiveSessionInfo]:
    """Return all currently active checkout sessions (for the 'check in for someone' flow)."""
    return list_active_sessions(db)


@router.patch(
    "/{session_id}/checkin",
    response_model=SessionResponse,
    dependencies=[Depends(verify_kiosk_key)],
)
def checkin(
    req: CheckinRequest,
    session_id: int = Path(...),
    db: Session = Depends(get_db),
) -> SessionResponse:
    """Mark an active session as returned. Any valid member card may check in any session."""
    try:
        return complete_checkin(db, req, session_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))
