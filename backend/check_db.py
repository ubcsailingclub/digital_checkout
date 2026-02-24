import os
import sqlite3

db_path = os.path.abspath("digital_checkout.db")
print("Opening DB:", db_path)

conn = sqlite3.connect(db_path)
cur = conn.cursor()

# Show tables first (helps diagnose instantly)
tables = cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;").fetchall()
print("tables:", tables)

# Safe checks
for table in ["members", "member_cards"]:
    try:
        count = cur.execute(f"SELECT COUNT(*) FROM {table};").fetchone()[0]
        print(f"{table} count:", count)
    except Exception as e:
        print(f"{table} check error:", e)

try:
    rows = cur.execute("""
    SELECT *
    FROM member_cards
    WHERE card_uid = '61699'
       OR card_uid_normalized = '61699';
    """).fetchall()
    print("matches for 61699:", rows)
except Exception as e:
    print("61699 lookup error:", e)

conn.close()