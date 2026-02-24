"""Fix BIGINT primary keys for SQLite autoincrement compatibility

SQLite only auto-increments a column declared as exactly INTEGER PRIMARY KEY
(the ROWID alias). BIGINT PRIMARY KEY does NOT auto-increment, causing
NOT NULL constraint failures on insert.

Revision ID: 003
Revises: 002
Create Date: 2026-02-24 00:00:00.000000

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "003"
down_revision: Union[str, None] = "002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # batch_alter_table with recreate="always" rebuilds each table so Alembic
    # can emit the corrected DDL for SQLite (ALTER COLUMN is not supported).
    # checkout_sessions must be done first since session_crew_members FK refs it.
    with op.batch_alter_table("checkout_sessions", recreate="always") as batch_op:
        batch_op.alter_column(
            "id",
            existing_type=sa.BigInteger(),
            type_=sa.Integer(),
            autoincrement=True,
            existing_nullable=False,
        )

    with op.batch_alter_table("session_crew_members", recreate="always") as batch_op:
        batch_op.alter_column(
            "id",
            existing_type=sa.BigInteger(),
            type_=sa.Integer(),
            autoincrement=True,
            existing_nullable=False,
        )


def downgrade() -> None:
    with op.batch_alter_table("session_crew_members", recreate="always") as batch_op:
        batch_op.alter_column(
            "id",
            existing_type=sa.Integer(),
            type_=sa.BigInteger(),
            autoincrement=True,
            existing_nullable=False,
        )

    with op.batch_alter_table("checkout_sessions", recreate="always") as batch_op:
        batch_op.alter_column(
            "id",
            existing_type=sa.Integer(),
            type_=sa.BigInteger(),
            autoincrement=True,
            existing_nullable=False,
        )
