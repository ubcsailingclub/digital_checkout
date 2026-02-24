"""
Seed the craft table from fleet.json.
Run from the backend/ directory:
    python scripts/seed_crafts.py

Safe to re-run — uses upsert logic (insert or update on conflict).
"""

import json
import sys
from pathlib import Path

# Allow `from app.xxx import ...`
sys.path.insert(0, str(Path(__file__).parent.parent))

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.session import SessionLocal, engine
from app.models.base import Base
from app.models.craft import Craft

FLEET_JSON = (
    Path(__file__).parent.parent.parent
    / "android-app" / "app" / "src" / "main" / "assets" / "fleet.json"
)

# Map craft class → fleet_type category label (matches CraftSelectScreen logic)
def fleet_type(craft_class: str) -> str:
    upper = craft_class.upper().strip()
    if upper.startswith("WINDSURFER"):
        return "WINDSURF"
    if upper in {"RS QUEST", "RS QUEST SPINNAKER", "LASER", "VANGUARD 15",
                 "RS500", "RS800", "HOBIE 16", "NACRA F18"}:
        return "SAILING"
    if upper in {"KAYAK", "SUP"}:
        return upper
    return "OTHER"


def main() -> None:
    data = json.loads(FLEET_JSON.read_text(encoding="utf-8"))

    with SessionLocal() as db:
        inserted = 0
        updated = 0

        next_id = 1  # explicit IDs for SQLite BIGINT NOT NULL compatibility

        for fleet in data["fleets"]:
            craft_class = fleet["class"]
            ftype = fleet_type(craft_class)

            for boat in fleet["boats"]:
                code = boat["code"]
                name = boat["name"]

                existing = db.execute(
                    select(Craft).where(Craft.craft_code == code)
                ).scalar_one_or_none()

                if existing is None:
                    db.add(Craft(
                        id           = next_id,
                        craft_code   = code,
                        display_name = name,
                        fleet_type   = ftype,
                        craft_class  = craft_class,
                        is_active    = True,
                        status       = "available",
                        metadata_json = {},
                    ))
                    inserted += 1
                else:
                    # Update name/class in case fleet.json changed
                    existing.display_name = name
                    existing.craft_class  = craft_class
                    existing.fleet_type   = ftype
                    updated += 1

                next_id += 1

        db.commit()
        print(f"Done — {inserted} inserted, {updated} updated")


if __name__ == "__main__":
    main()
