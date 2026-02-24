"""Initial schema

Revision ID: 001
Revises:
Create Date: 2026-02-23 00:00:00.000000

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "members",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("wa_contact_id", sa.BigInteger(), nullable=False),
        sa.Column("full_name", sa.Text(), nullable=False),
        sa.Column("first_name", sa.Text(), nullable=True),
        sa.Column("last_name", sa.Text(), nullable=True),
        sa.Column("email", sa.Text(), nullable=True),
        sa.Column("membership_status", sa.String(50), nullable=False, server_default="unknown"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("wa_contact_id"),
    )
    op.create_index("idx_members_active", "members", ["is_active"])
    op.create_index("idx_members_name", "members", ["full_name"])
    op.create_index("idx_members_wa_contact_id", "members", ["wa_contact_id"])

    op.create_table(
        "member_cards",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("member_id", sa.BigInteger(), nullable=False),
        sa.Column("card_uid", sa.Text(), nullable=False),
        sa.Column("card_uid_normalized", sa.Text(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("label", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.ForeignKeyConstraint(["member_id"], ["members.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("card_uid_normalized", name="uq_member_cards_card_uid_normalized"),
    )
    op.create_index("idx_member_cards_member_id", "member_cards", ["member_id"])
    op.create_index("idx_member_cards_card_uid_norm", "member_cards", ["card_uid_normalized"])

    op.create_table(
        "craft",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("craft_code", sa.String(100), nullable=False),
        sa.Column("display_name", sa.Text(), nullable=False),
        sa.Column("fleet_type", sa.String(50), nullable=False),
        sa.Column("craft_class", sa.String(100), nullable=True),
        sa.Column("capacity", sa.Integer(), nullable=True),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("requires_checkout", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("status", sa.String(50), nullable=False, server_default="available"),
        sa.Column("status_reason", sa.Text(), nullable=True),
        sa.Column("config_version", sa.String(100), nullable=True),
        sa.Column("metadata_json", sa.JSON(), nullable=False, server_default="{}"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("craft_code"),
    )
    op.create_index("idx_craft_status", "craft", ["status"])
    op.create_index("idx_craft_fleet_type", "craft", ["fleet_type"])
    op.create_index("idx_craft_active", "craft", ["is_active"])

    op.create_table(
        "checkout_sessions",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("member_id", sa.BigInteger(), nullable=False),
        sa.Column("craft_id", sa.BigInteger(), nullable=False),
        sa.Column("checkout_time", sa.DateTime(timezone=True), nullable=False),
        sa.Column("checkin_time", sa.DateTime(timezone=True), nullable=True),
        sa.Column("status", sa.String(50), nullable=False, server_default="active"),
        sa.Column("expected_return_time", sa.DateTime(timezone=True), nullable=True),
        sa.Column("purpose", sa.Text(), nullable=True),
        sa.Column("party_size", sa.Integer(), nullable=True),
        sa.Column("notes_out", sa.Text(), nullable=True),
        sa.Column("notes_in", sa.Text(), nullable=True),
        sa.Column("damage_reported", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("checkout_method", sa.String(50), nullable=False, server_default="self_service"),
        sa.Column("checkin_method", sa.String(50), nullable=True),
        sa.Column("checkout_actor", sa.Text(), nullable=True),
        sa.Column("checkin_actor", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.CheckConstraint(
            "checkin_time IS NULL OR checkin_time >= checkout_time",
            name="chk_checkin_after_checkout",
        ),
        sa.ForeignKeyConstraint(["member_id"], ["members.id"]),
        sa.ForeignKeyConstraint(["craft_id"], ["craft.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("idx_checkout_sessions_member_id", "checkout_sessions", ["member_id"])
    op.create_index("idx_checkout_sessions_craft_id", "checkout_sessions", ["craft_id"])
    op.create_index("idx_checkout_sessions_status", "checkout_sessions", ["status"])

    op.create_table(
        "session_crew_members",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("session_id", sa.BigInteger(), nullable=False),
        sa.Column("member_id", sa.BigInteger(), nullable=True),
        sa.Column("display_name", sa.Text(), nullable=False),
        sa.Column("is_guest", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.ForeignKeyConstraint(["session_id"], ["checkout_sessions.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["member_id"], ["members.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("idx_crew_session_id", "session_crew_members", ["session_id"])
    op.create_index("idx_crew_member_id", "session_crew_members", ["member_id"])


def downgrade() -> None:
    op.drop_table("session_crew_members")
    op.drop_table("checkout_sessions")
    op.drop_table("craft")
    op.drop_table("member_cards")
    op.drop_table("members")
