import logging
import os
from contextlib import asynccontextmanager

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from sqlalchemy.orm import Session

from app.api import admin, sync
from app.db.session import engine

logger = logging.getLogger(__name__)


def _run_member_sync() -> None:
    try:
        from scripts.sync_members import _get_token, _get_all_contacts, _sync
        from app.core.config import settings

        if not settings.wa_api_key or not settings.wa_account_id:
            logger.warning("Member sync skipped — WA credentials not configured.")
            return

        logger.info("Scheduled member sync starting…")
        token    = _get_token(settings.wa_api_key)
        contacts = _get_all_contacts(token, settings.wa_account_id)
        with Session(engine) as session:
            _sync(session, contacts, token, settings.wa_account_id)
        logger.info("Scheduled member sync complete.")
    except Exception:
        logger.exception("Scheduled member sync failed.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    run_sync = os.getenv("RUN_MEMBER_SYNC_ON_STARTUP", "false").lower() == "true"

    scheduler = BackgroundScheduler()

    if run_sync:
        scheduler.add_job(_run_member_sync, "interval", hours=1, id="member_sync")
        logger.info("Member sync scheduler enabled (hourly).")
    else:
        logger.info("Member sync scheduler disabled (RUN_MEMBER_SYNC_ON_STARTUP != true).")

    scheduler.start()

    if run_sync:
        _run_member_sync()  # eager first run

    yield

    scheduler.shutdown()


app = FastAPI(title="UBCSC Fleet Manager", version="2.0.0", lifespan=lifespan)

app.include_router(sync.router,  prefix="/api/v1")
app.include_router(admin.router)   # /admin — browser-facing, no API prefix


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
