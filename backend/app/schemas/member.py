from pydantic import BaseModel


class ActiveCheckoutInfo(BaseModel):
    session_id: int
    craft_code: str
    craft_name: str

    model_config = {"from_attributes": True}


class MemberKioskResponse(BaseModel):
    """Minimum PII response for kiosk display. No email, WA ID, or membership dates."""

    id: int
    display_name: str           # first name only or "First L."
    has_active_checkout: bool
    active_checkout: ActiveCheckoutInfo | None
    certifications: list[str]   # e.g. ["Laser", "Windsurfer L1"]

    model_config = {"from_attributes": True}
