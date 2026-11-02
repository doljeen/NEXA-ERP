from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str

    # Local LLM (vLLM on GPU server, via SSAFY VPN)
    local_llm_base_url: str = ""
    local_llm_api_key: str = ""
    local_llm_model: str = "qwen3.8-27b"
    local_llm_timeout: float = 60.0

    # GMS (fallback when the local LLM is down)
    GMS_API_KEY: str = ""
    gms_base_url: str = ""
    gms_model: str = ""

    # 개발용 LLM 테스트 API 등록 여부 (운영에서는 False 유지)
    llm_test_api_enabled: bool = False

    eureka_server_url: str = ""
    instance_host: str = ""
    instance_port: int = 8086

    upload_dir: str = "uploads"
    max_upload_mb: int = 20

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


    @property
    def database_url(self) -> str:
        return (
            f"postgresql+psycopg://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    @property
    def checkpoint_url(self) -> str:
        return(
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()

