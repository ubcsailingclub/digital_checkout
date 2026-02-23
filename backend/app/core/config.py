from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "UBCSC Digital Checkout API"
    environment: str = "development"
    debug: bool = True

    host: str = "0.0.0.0"
    port: int = 8000

    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5432/digital_checkout"

    # Wild Apricot (backend only)
    wa_client_id: str | None = None
    wa_client_secret: str | None = None
    wa_refresh_token: str | None = None

    # Example kiosk key placeholder
    kiosk_api_key_example: str | None = None

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


settings = Settings()
