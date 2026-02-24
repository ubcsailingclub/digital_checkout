import sqlite3

conn = sqlite3.connect("digital_checkout.db")
cur = conn.cursor()

print("member_cards count:", cur.execute("SELECT COUNT(*) FROM member_cards;").fetchone()[0])
print("members count:", cur.execute("SELECT COUNT(*) FROM members;").fetchone()[0])

rows = cur.execute("""
SELECT *
FROM member_cards
WHERE card_uid = '61699'
   OR card_uid_normalized = '61699';
""").fetchall()

print("matches for 61699:", rows)

conn.close()