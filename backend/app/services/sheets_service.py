"""Google Apps Script webhook — appends a row to the Damage Reports sheet.

Fire-and-forget: errors are logged but never raised so a webhook failure
cannot block or roll back a check-in.
"""

import logging
from datetime import datetime, timezone

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


def post_damage_report(
    *,
    member_name: str,
    craft_name: str,
    notes: str | None,
    session_id: int,
) -> None:
    url = settings.damage_report_webhook_url
    if not url:
        logger.debug("Damage report webhook not configured — skipping sheet update")
        return

    # Vancouver is UTC-7 / UTC-8; keep it simple and send UTC with label
    timestamp = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    payload = {
        "secret":      settings.damage_report_webhook_secret,
        "timestamp":   timestamp,
        "member_name": member_name,
        "craft_name":  craft_name,
        "session_id":  session_id,
        "notes":       notes or "",
    }

    try:
        resp = httpx.post(url, json=payload, timeout=10.0, follow_redirects=True)
        if resp.status_code == 200:
            logger.info("Damage report posted to sheet (session %s)", session_id)
        else:
            logger.warning(
                "Sheets webhook returned %s for session %s: %s",
                resp.status_code, session_id, resp.text
            )
    except Exception as exc:
        logger.warning("Sheets webhook failed for session %s: %s", session_id, exc)
