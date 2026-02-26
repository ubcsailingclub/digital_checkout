"""Google Apps Script webhooks — append rows to Google Sheets.

Both functions are truly fire-and-forget: the HTTP call runs in a daemon
thread so it never blocks or slows down an API response. Errors are logged
but never raised, so a webhook failure cannot affect any checkout/check-in.
"""

import logging
import threading
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
    """Append a row to the Damage Reports sheet."""
    url = settings.damage_report_webhook_url
    if not url:
        logger.debug("Damage report webhook not configured — skipping sheet update")
        return

    timestamp = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    payload = {
        "secret":      settings.damage_report_webhook_secret,
        "timestamp":   timestamp,
        "member_name": member_name,
        "craft_name":  craft_name,
        "session_id":  session_id,
        "notes":       notes or "",
    }

    def _send() -> None:
        try:
            resp = httpx.post(url, json=payload, timeout=30.0, follow_redirects=True)
            if resp.status_code == 200:
                logger.info("Damage report posted to sheet (session %s)", session_id)
            else:
                logger.warning(
                    "Damage webhook returned %s for session %s: %s",
                    resp.status_code, session_id, resp.text,
                )
        except Exception as exc:
            logger.warning("Damage webhook failed for session %s: %s", session_id, exc)

    threading.Thread(target=_send, daemon=True).start()


def post_checkout_event(
    *,
    event_type: str,           # "checkout" | "checkin" | "auto_expired"
    member_name: str,
    craft_name: str,
    craft_code: str,
    session_id: int,
    party_size: int | None = None,
    expected_return_time: datetime | None = None,
    checkout_time: datetime | None = None,
    checkin_time: datetime | None = None,
    notes: str | None = None,
    damage_reported: bool = False,
) -> None:
    """Append a row to the live Checkout Log sheet."""
    url = settings.checkout_log_webhook_url
    if not url:
        logger.debug("Checkout log webhook not configured — skipping sheet update")
        return

    def fmt(dt: datetime | None) -> str:
        if dt is None:
            return ""
        # Ensure UTC-aware before formatting
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.strftime("%Y-%m-%d %H:%M UTC")

    duration_mins: int | None = None
    if checkout_time and checkin_time:
        ct = checkout_time if checkout_time.tzinfo else checkout_time.replace(tzinfo=timezone.utc)
        ci = checkin_time  if checkin_time.tzinfo  else checkin_time.replace(tzinfo=timezone.utc)
        duration_mins = max(0, int((ci - ct).total_seconds() / 60))

    payload = {
        "secret":               settings.checkout_log_webhook_secret,
        "event_type":           event_type,
        "timestamp":            fmt(datetime.now(tz=timezone.utc)),
        "member_name":          member_name,
        "craft_name":           craft_name,
        "craft_code":           craft_code,
        "session_id":           session_id,
        "party_size":           party_size or "",
        "expected_return_time": fmt(expected_return_time),
        "checkout_time":        fmt(checkout_time),
        "checkin_time":         fmt(checkin_time),
        "duration_mins":        duration_mins if duration_mins is not None else "",
        "notes":                notes or "",
        "damage_reported":      "Yes" if damage_reported else "No",
    }

    def _send() -> None:
        try:
            resp = httpx.post(url, json=payload, timeout=30.0, follow_redirects=True)
            if resp.status_code == 200:
                logger.info("Checkout log posted to sheet (session %s, type=%s)", session_id, event_type)
            else:
                logger.warning(
                    "Checkout log webhook returned %s for session %s: %s",
                    resp.status_code, session_id, resp.text,
                )
        except Exception as exc:
            logger.warning("Checkout log webhook failed for session %s: %s", session_id, exc)

    threading.Thread(target=_send, daemon=True).start()
