from pydantic import BaseModel


class MemberListItem(BaseModel):
    """Minimal member record used to populate the name-search dropdown on the kiosk."""
    id: int
    display_name: str   # full name for searchability, e.g. "Alice Smith"

    model_config = {"from_attributes": True}


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
