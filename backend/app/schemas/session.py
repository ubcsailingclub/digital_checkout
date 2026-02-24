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
