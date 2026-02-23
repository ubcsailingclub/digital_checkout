from app.db.session import engine
from app.models import Base  # noqa: F401 (ensures models are imported)


def main() -> None:
    Base.metadata.create_all(bind=engine)
    print("Tables created successfully.")


if __name__ == "__main__":
    main()
