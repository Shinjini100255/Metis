from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    service_name: str = "metis-ml-api"
    version: str = "0.1.0"
    log_level: str = os.getenv("LOG_LEVEL", "info")


settings = Settings()
