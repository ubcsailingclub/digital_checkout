from pydantic import BaseModel


class CraftResponse(BaseModel):
    id: int
    code: str
    display_name: str
    craft_class: str | None
    is_available: bool  # computed from active sessions at query time

    model_config = {"from_attributes": True}
