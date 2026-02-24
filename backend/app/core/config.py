from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "UBCSC Digital Checkout API"
    environment: str = "development"
    debug: bool = True

    host: str = "0.0.0.0"
    port: int = 8000

    database_url: str = "sqlite:///./digital_checkout.db"

    # Wild Apricot — API key auth (same pattern as BlackbeardBot)
    wa_api_key: str | None = None
    wa_account_id: int | None = None

    # Kiosk authentication — set a strong random value in .env
    kiosk_api_key: str = "change-me-in-dotenv"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


settings = Settings()
