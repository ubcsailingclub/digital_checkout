from datetime import datetime

from pydantic import BaseModel


class CrewInput(BaseModel):
    name: str
    is_guest: bool = False
    card_uid: str | None = None  # set when crew member scanned their card


class SessionCreate(BaseModel):
    card_uid: str
    craft_id: int
    crew: list[CrewInput] = []
    expected_return_hours: int | None = None  # None = no ETR set


class CheckinRequest(BaseModel):
    card_uid: str
    notes_in: str | None = None
    damage_reported: bool = False


class SessionResponse(BaseModel):
    session_id: int
    status: str
    message: str


class ActiveSessionInfo(BaseModel):
    """Summary of a currently active checkout, returned by GET /sessions/active."""
    session_id: int
    craft_id: int
    craft_code: str
    craft_name: str
    member_name: str
    checkout_time: datetime
    expected_return_time: datetime | None = None


class RecentSessionInfo(BaseModel):
    """A recent (active or completed) session for the idle-screen logbook."""
    session_id: int
    skipper_name: str
    crew_names: list[str]
    craft_name: str
    craft_code: str
    checkout_time: datetime
    expected_return_time: datetime | None = None
    checkin_time: datetime | None = None
    status: str  # "active" | "completed" | "auto_expired"
