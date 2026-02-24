"""
Manually register an NFC card UID for a member.

Usage (run from backend/ directory):
    python scripts/register_card.py

You'll be prompted for:
  - Member search term (name or email fragment)
  - Card UID (from the server WARNING log when card is not recognised)
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import engine
from app.models.member import Member, MemberCard
from app.services.member_service import normalize_card_uid


def _find_members(session: Session, query: str) -> list[Member]:
    q = f"%{query.lower()}%"
    return session.execute(
        select(Member).where(
            (func.lower(Member.full_name).like(q)) | (func.lower(Member.email).like(q))
        ).order_by(Member.full_name).limit(10)
    ).scalars().all()


def main() -> None:
    print("=== Card Registration ===\n")

    search = input("Search member by name or email: ").strip()
    if not search:
        print("No search term provided. Exiting.")
        sys.exit(1)

    with Session(engine) as session:
        members = _find_members(session, search)
        if not members:
            print(f"No members found matching {search!r}.")
            sys.exit(1)

        print(f"\nFound {len(members)} member(s):")
        for i, m in enumerate(members):
            status = "ACTIVE" if m.is_active else "inactive"
            print(f"  [{i}] {m.full_name}  ({m.email or 'no email'})  [{status}]")

        idx_str = input("\nEnter number to select member: ").strip()
        try:
            idx = int(idx_str)
            member = members[idx]
        except (ValueError, IndexError):
            print("Invalid selection. Exiting.")
            sys.exit(1)

        print(f"\nSelected: {member.full_name}")

        raw_uid = input("Enter card UID (as shown in server log): ").strip()
        if not raw_uid:
            print("No card UID provided. Exiting.")
            sys.exit(1)

        normalized = normalize_card_uid(raw_uid)
        print(f"  raw:        {raw_uid!r}")
        print(f"  normalized: {normalized!r}")

        # Check if card already exists
        existing = session.execute(
            select(MemberCard).where(MemberCard.card_uid_normalized == normalized)
        ).scalar_one_or_none()

        if existing:
            if existing.member_id == member.id:
                print("\nCard already registered to this member.")
                existing.is_active = True
                session.commit()
                print("Marked as active.")
            else:
                other = session.get(Member, existing.member_id)
                print(f"\nCard is registered to another member: {other.full_name if other else existing.member_id}")
                confirm = input("Reassign to the selected member? [y/N]: ").strip().lower()
                if confirm == "y":
                    existing.member_id = member.id
                    existing.is_active = True
                    session.commit()
                    print("Reassigned.")
                else:
                    print("Aborted.")
        else:
            next_id = (session.execute(select(func.max(MemberCard.id))).scalar() or 0) + 1
            session.add(MemberCard(
                id=next_id,
                member_id=member.id,
                card_uid=raw_uid,
                card_uid_normalized=normalized,
                is_active=True,
                label="Jericho Card",
            ))
            session.commit()
            print(f"\nCard registered for {member.full_name}.")

        print("\nDone!")


if __name__ == "__main__":
    main()
