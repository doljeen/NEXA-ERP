from pydantic import BaseModel

class CategoryResponse(BaseModel):
    code: str
    name: str