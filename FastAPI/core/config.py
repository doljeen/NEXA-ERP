from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str

    GMS_API_KEY: str = ""

    eureka_server_url: str = ""
    instance_host: str = ""
    instance_port: int = 8086

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


    @property
    def database_url(self) -> str:
        return (
            f"postgresql+psycopg://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()

