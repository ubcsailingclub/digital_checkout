import logging
from contextlib import asynccontextmanager

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import Depends, FastAPI
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.api import crafts, members, sessions
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
        token    = _get_token(settings.wa_api_key)
        contacts = _get_all_contacts(token, settings.wa_account_id)
        with Session(engine) as session:
            _sync(session, contacts)
        logger.info("Scheduled member sync complete.")
    except Exception:
        logger.exception("Scheduled member sync failed.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = BackgroundScheduler()
    # Run immediately on startup, then every 6 hours
    scheduler.add_job(_run_member_sync, "interval", hours=6, id="member_sync")
    scheduler.start()
    _run_member_sync()  # eager first run so the DB is populated right away
    yield
    scheduler.shutdown()


app = FastAPI(title="UBCSC Digital Checkout API", version="0.1.0", lifespan=lifespan)

# ---------------------------------------------------------------------------
# API v1 routers
# ---------------------------------------------------------------------------

API_PREFIX = "/api/v1"

app.include_router(crafts.router,  prefix=API_PREFIX)
app.include_router(members.router, prefix=API_PREFIX)
app.include_router(sessions.router, prefix=API_PREFIX)


# ---------------------------------------------------------------------------
# Health / root
# ---------------------------------------------------------------------------

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
