from pydantic import BaseModel

class CategoryResponse(BaseModel):
    code: str
    name: str


class NodeSummary(BaseModel):
    node_type: str
    name: str
    category: str
    description: str


class ConfigField(BaseModel):
    key: str
    label: str
    type: str
    required: bool = False
    options: list[str] = []


class OutputVar(BaseModel):
    key: str
    label: str
    type: str


class NodeDetail(NodeSummary):
    config_fields: list[ConfigField] = []
    branches: list[str] = []
    outputs: list[OutputVar] = []


