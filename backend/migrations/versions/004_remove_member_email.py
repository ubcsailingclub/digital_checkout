"""Remove email column from members table

Revision ID: 004
Revises: 003
Create Date: 2026-03-29 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa

revision = "004"
down_revision = "003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("members") as batch_op:
        batch_op.drop_column("email")


def downgrade() -> None:
    with op.batch_alter_table("members") as batch_op:
        batch_op.add_column(sa.Column("email", sa.Text(), nullable=True))
