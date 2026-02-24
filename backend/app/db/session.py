from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker

from app.core.config import settings

engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    future=True,
    # SQLite: wait up to 30 s for a locked DB rather than failing immediately
    connect_args={"timeout": 30},
)


@event.listens_for(engine, "connect")
def _set_sqlite_pragmas(dbapi_conn, _record) -> None:
    """Configure SQLite for concurrent access on every new connection.

    WAL (Write-Ahead Logging) mode lets readers proceed without blocking
    while a write transaction is open — critical during the 8-minute member
    sync.  SYNCHRONOUS=NORMAL is safe with WAL and noticeably faster than
    the default FULL.
    """
    cursor = dbapi_conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA synchronous=NORMAL")
    cursor.close()


SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    autocommit=False,
    future=True,
)
