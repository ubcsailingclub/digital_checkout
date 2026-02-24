from datetime import datetime

from pydantic import BaseModel


class CraftResponse(BaseModel):
    id: int
    code: str
    display_name: str
    craft_class: str | None
    is_available: bool               # computed from active sessions at query time
    expected_return_time: datetime | None = None  # ETR of the active session, if checked out

    model_config = {"from_attributes": True}
