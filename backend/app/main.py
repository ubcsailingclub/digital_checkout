import logging
import os
from contextlib import asynccontextmanager

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import Depends, FastAPI
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.api import admin, crafts, members, sessions
from app.db.deps import get_db

logger = logging.getLogger(__name__)


def _run_member_sync() -> None:
    """Runs the WA member sync in the background scheduler thread."""
    try:
        from scripts.sync_members import _get_token, _get_all_contacts, _sync
        from app.core.config import settings
        from app.db.session import engine

        if not settings.wa_api_key or not settings.wa_account_id:
            logger.warning("Member sync skipped — WA credentials not configured.")
            return

        logger.info("Scheduled member sync starting…")
        token = _get_token(settings.wa_api_key)
        contacts = _get_all_contacts(token, settings.wa_account_id)
        with Session(engine) as session:
            _sync(session, contacts, token, settings.wa_account_id)  # <-- fixed
        logger.info("Scheduled member sync complete.")
    except Exception:
        logger.exception("Scheduled member sync failed.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = None

    # Default OFF for local dev unless explicitly enabled
    run_sync = os.getenv("RUN_MEMBER_SYNC_ON_STARTUP", "false").lower() == "true"

    if run_sync:
        scheduler = BackgroundScheduler()
        # Run every 6 hours
        scheduler.add_job(_run_member_sync, "interval", hours=6, id="member_sync")
        scheduler.start()
        _run_member_sync()  # eager first run (only if enabled)
        logger.info("Member sync scheduler enabled.")
    else:
        logger.info("Member sync scheduler disabled (RUN_MEMBER_SYNC_ON_STARTUP != true).")

    yield

    if scheduler is not None:
        scheduler.shutdown()


app = FastAPI(title="UBCSC Digital Checkout API", version="0.1.0", lifespan=lifespan)

API_PREFIX = "/api/v1"
app.include_router(crafts.router, prefix=API_PREFIX)
app.include_router(members.router, prefix=API_PREFIX)
app.include_router(sessions.router, prefix=API_PREFIX)
app.include_router(admin.router)  # /admin — no API prefix, browser-facing


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/db")
def health_db(db: Session = Depends(get_db)) -> dict[str, str]:
    db.execute(text("SELECT 1"))
    return {"status": "ok"}


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "UBCSC Digital Checkout API"}