"""Admin web interface — boat status management.

Access at: http://<server>:8000/admin?key=<ADMIN_API_KEY>
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.deps import get_db
from app.models.craft import Craft

router = APIRouter(prefix="/admin", tags=["admin"])


# ---------------------------------------------------------------------------
# Auth helper
# ---------------------------------------------------------------------------

def _check_key(key: str) -> None:
    if key != settings.admin_api_key:
        raise HTTPException(status_code=401, detail="Invalid admin key")


# ---------------------------------------------------------------------------
# HTML page
# ---------------------------------------------------------------------------

def _escape(text: str) -> str:
    return (text or "").replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;").replace(">", "&gt;")


def _build_html(crafts: list[Craft], key: str) -> str:
    # Group by class
    grouped: dict[str, list[Craft]] = {}
    for c in crafts:
        grouped.setdefault(c.craft_class, []).append(c)

    fleet_blocks = ""
    for class_name in sorted(grouped):
        rows = ""
        for c in grouped[class_name]:
            available   = c.status == "available"
            dot_cls     = "dot-on" if available else "dot-off"
            btn_label   = "Disable" if available else "Enable"
            btn_cls     = "btn-disable" if available else "btn-enable"
            reason_val  = _escape(c.status_reason or "")
            rows += f"""
              <tr>
                <td><span class="dot {dot_cls}"></span></td>
                <td class="boat-name">{_escape(c.display_name)}</td>
                <td class="code"><code>{_escape(c.craft_code)}</code></td>
                <td class="reason-cell">
                  <input type="text" id="r{c.id}" value="{reason_val}" placeholder="Reason…">
                </td>
                <td>
                  <button class="{btn_cls}"
                    onclick="toggle({c.id},'{c.status}','{_escape(key)}','r{c.id}')">
                    {btn_label}
                  </button>
                </td>
              </tr>"""

        fleet_blocks += f"""
        <div class="fleet-card">
          <div class="fleet-title">{_escape(class_name)}</div>
          <table><tbody>{rows}</tbody></table>
        </div>"""

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>UBCSC Admin — Boat Status</title>
  <style>
    *{{box-sizing:border-box;margin:0;padding:0}}
    body{{background:#0D1B2A;color:#fff;font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:28px}}
    h1{{color:#4DD0E1;font-size:22px;margin-bottom:4px}}
    .sub{{color:#90CAF9;font-size:13px;margin-bottom:24px}}
    .grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(400px,1fr));gap:16px}}
    .fleet-card{{background:#162032;border:1px solid #263749;border-radius:12px;padding:16px}}
    .fleet-title{{color:#90CAF9;font-size:11px;letter-spacing:1.5px;font-weight:700;text-transform:uppercase;margin-bottom:10px}}
    table{{width:100%;border-collapse:collapse}}
    tr{{border-bottom:1px solid #263749}}
    tr:last-child{{border-bottom:none}}
    td{{padding:7px 5px;vertical-align:middle;font-size:13px}}
    .dot{{display:inline-block;width:9px;height:9px;border-radius:50%;flex-shrink:0}}
    .dot-on{{background:#43A047}}
    .dot-off{{background:#E53935}}
    .boat-name{{max-width:180px}}
    .code{{color:#546E7A;font-size:12px}}
    .reason-cell input{{background:#0D1B2A;border:1px solid #263749;color:#fff;border-radius:6px;
      padding:3px 7px;font-size:12px;width:130px}}
    .reason-cell input:focus{{outline:none;border-color:#00ACC1}}
    button{{border:none;border-radius:8px;padding:5px 13px;cursor:pointer;font-weight:600;font-size:12px;min-width:66px}}
    .btn-disable{{background:#b71c1c;color:#fff}}
    .btn-disable:hover{{background:#e53935}}
    .btn-enable{{background:#1b5e20;color:#fff}}
    .btn-enable:hover{{background:#43A047}}
    #toast{{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#263749;color:#fff;
      padding:10px 20px;border-radius:8px;font-size:13px;display:none;z-index:999}}
  </style>
</head>
<body>
  <h1>🚤 UBCSC Boat Status</h1>
  <p class="sub">Toggle availability below. Add a reason when disabling a boat. Page reloads after each change.</p>
  <div class="grid">{fleet_blocks}</div>
  <div id="toast"></div>
  <script>
    function showToast(msg, ok) {{
      const t = document.getElementById('toast');
      t.textContent = msg;
      t.style.background = ok ? '#1b5e20' : '#b71c1c';
      t.style.display = 'block';
      setTimeout(() => {{ t.style.display='none'; window.location.reload(); }}, 1200);
    }}
    async function toggle(id, currentStatus, key, reasonId) {{
      const newStatus = currentStatus === 'available' ? 'unavailable' : 'available';
      const reason = newStatus === 'unavailable'
        ? document.getElementById(reasonId).value.trim()
        : null;
      try {{
        const r = await fetch('/admin/crafts/' + id + '/status?key=' + encodeURIComponent(key), {{
          method: 'PATCH',
          headers: {{'Content-Type': 'application/json'}},
          body: JSON.stringify({{status: newStatus, reason: reason || null}})
        }});
        if (r.ok) showToast(newStatus === 'available' ? '✓ Enabled' : '⚠ Disabled', r.ok);
        else showToast('Error: ' + await r.text(), false);
      }} catch(e) {{ showToast('Network error', false); }}
    }}
  </script>
</body>
</html>"""


@router.get("", response_class=HTMLResponse)
def admin_page(
    key: str = Query(..., description="Admin API key"),
    db:  Session = Depends(get_db)
) -> str:
    _check_key(key)
    crafts = db.execute(
        select(Craft)
        .where(Craft.is_active == True)
        .order_by(Craft.craft_class, Craft.display_name)
    ).scalars().all()
    return _build_html(list(crafts), key)


# ---------------------------------------------------------------------------
# Status toggle endpoint
# ---------------------------------------------------------------------------

class StatusUpdate(BaseModel):
    status: str   # "available" | "unavailable"
    reason: str | None = None


@router.patch("/crafts/{craft_id}/status")
def update_craft_status(
    craft_id: int,
    body:     StatusUpdate,
    key:      str = Query(...),
    db:       Session = Depends(get_db)
) -> dict:
    _check_key(key)
    if body.status not in ("available", "unavailable"):
        raise HTTPException(400, "status must be 'available' or 'unavailable'")

    craft = db.get(Craft, craft_id)
    if craft is None:
        raise HTTPException(404, "Craft not found")

    craft.status        = body.status
    craft.status_reason = body.reason or None
    db.commit()
    return {"ok": True, "craft_code": craft.craft_code, "status": craft.status}
