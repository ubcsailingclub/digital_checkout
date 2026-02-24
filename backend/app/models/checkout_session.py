from datetime import datetime

from sqlalchemy import Boolean, CheckConstraint, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base
from app.models.mixins import TimestampMixin


class CheckoutSession(TimestampMixin, Base):
    __tablename__ = "checkout_sessions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)

    member_id: Mapped[int] = mapped_column(ForeignKey("members.id"), nullable=False)
    craft_id: Mapped[int] = mapped_column(ForeignKey("craft.id"), nullable=False)

    checkout_time: Mapped[datetime] = mapped_column(nullable=False)
    checkin_time: Mapped[datetime | None] = mapped_column(nullable=True)

    status: Mapped[str] = mapped_column(String(50), default="active", nullable=False)

    expected_return_time: Mapped[datetime | None] = mapped_column(nullable=True)
    purpose: Mapped[str | None] = mapped_column(Text, nullable=True)
    party_size: Mapped[int | None] = mapped_column(Integer, nullable=True)

    notes_out: Mapped[str | None] = mapped_column(Text, nullable=True)
    notes_in: Mapped[str | None] = mapped_column(Text, nullable=True)

    damage_reported: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    checkout_method: Mapped[str] = mapped_column(String(50), default="self_service", nullable=False)
    checkin_method: Mapped[str | None] = mapped_column(String(50), nullable=True)

    checkout_actor: Mapped[str | None] = mapped_column(Text, nullable=True)
    checkin_actor: Mapped[str | None] = mapped_column(Text, nullable=True)

    __table_args__ = (
        CheckConstraint(
            "checkin_time IS NULL OR checkin_time >= checkout_time",
            name="chk_checkin_after_checkout",
        ),
        Index("idx_checkout_sessions_member_id", "member_id"),
        Index("idx_checkout_sessions_craft_id", "craft_id"),
        Index("idx_checkout_sessions_status", "status"),
    )
