from __future__ import annotations

from sqlalchemy import Boolean, ForeignKey, Index, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base
from app.models.mixins import TimestampMixin


class SessionCrewMember(TimestampMixin, Base):
    __tablename__ = "session_crew_members"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)

    session_id: Mapped[int] = mapped_column(
        ForeignKey("checkout_sessions.id", ondelete="CASCADE"), nullable=False
    )
    # Null for guests who are not in the member DB
    member_id: Mapped[int | None] = mapped_column(
        ForeignKey("members.id", ondelete="SET NULL"), nullable=True
    )

    # Name recorded at checkout time — never updated retroactively
    display_name: Mapped[str] = mapped_column(Text, nullable=False)
    is_guest: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    __table_args__ = (
        Index("idx_crew_session_id", "session_id"),
        Index("idx_crew_member_id", "member_id"),
    )
