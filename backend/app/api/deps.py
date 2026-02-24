from fastapi import Header, HTTPException, status

from app.core.config import settings


def verify_kiosk_key(x_kiosk_key: str = Header(..., alias="X-Kiosk-Key")) -> None:
    """Dependency: validates the X-Kiosk-Key header on every API request."""
    if x_kiosk_key != settings.kiosk_api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing kiosk API key",
        )
