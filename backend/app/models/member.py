from __future__ import annotations

import json

from sqlalchemy import Boolean, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base
from app.models.mixins import TimestampMixin


class Member(TimestampMixin, Base):
    __tablename__ = "members"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    wa_contact_id: Mapped[int] = mapped_column(Integer, unique=True, nullable=False)

    full_name: Mapped[str] = mapped_column(Text, nullable=False)
    first_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_name: Mapped[str | None] = mapped_column(Text, nullable=True)

    membership_status: Mapped[str] = mapped_column(String(50), default="unknown", nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    # JSON-encoded list of certified craft classes, e.g. ["Laser", "RS Quest"]
    # Populated during WA sync; "*" means all-access (exec/instructor).
    certifications_json: Mapped[str | None] = mapped_column(Text, nullable=True)

    @property
    def certifications(self) -> list[str]:
        if not self.certifications_json:
            return []
        try:
            return json.loads(self.certifications_json)
        except Exception:
            return []

    cards: Mapped[list["MemberCard"]] = relationship(
        back_populates="member",
        cascade="all, delete-orphan",
    )

    __table_args__ = (
        Index("idx_members_active", "is_active"),
        Index("idx_members_name", "full_name"),
        Index("idx_members_wa_contact_id", "wa_contact_id"),
    )


class MemberCard(TimestampMixin, Base):
    __tablename__ = "member_cards"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    member_id: Mapped[int] = mapped_column(ForeignKey("members.id", ondelete="CASCADE"), nullable=False)

    card_uid: Mapped[str] = mapped_column(Text, nullable=False)
    card_uid_normalized: Mapped[str] = mapped_column(Text, nullable=False)

    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    label: Mapped[str | None] = mapped_column(Text, nullable=True)

    member: Mapped["Member"] = relationship(back_populates="cards")

    __table_args__ = (
        UniqueConstraint("card_uid_normalized", name="uq_member_cards_card_uid_normalized"),
        Index("idx_member_cards_member_id", "member_id"),
        Index("idx_member_cards_card_uid_norm", "card_uid_normalized"),
    )
