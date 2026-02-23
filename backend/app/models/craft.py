from sqlalchemy import BIGINT, Boolean, Index, Integer, JSON, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base
from app.models.mixins import TimestampMixin


class Craft(TimestampMixin, Base):
    __tablename__ = "craft"

    id: Mapped[int] = mapped_column(BIGINT, primary_key=True, autoincrement=True)

    craft_code: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    display_name: Mapped[str] = mapped_column(Text, nullable=False)

    fleet_type: Mapped[str] = mapped_column(String(50), nullable=False)
    craft_class: Mapped[str | None] = mapped_column(String(100), nullable=True)

    capacity: Mapped[int | None] = mapped_column(Integer, nullable=True)

    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    requires_checkout: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    status: Mapped[str] = mapped_column(String(50), default="available", nullable=False)
    status_reason: Mapped[str | None] = mapped_column(Text, nullable=True)

    config_version: Mapped[str | None] = mapped_column(String(100), nullable=True)

    metadata_json: Mapped[dict] = mapped_column(JSON, default=dict, nullable=False)

    __table_args__ = (
        Index("idx_craft_status", "status"),
        Index("idx_craft_fleet_type", "fleet_type"),
        Index("idx_craft_active", "is_active"),
    )
