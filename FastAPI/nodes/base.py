from dataclasses import dataclass, field


@dataclass
class NodeContext:
    run_id: int
    node_id: str
    config: dict
    inputs: dict
    outputs: dict


@dataclass
class NodeResult:
    output: dict = field(default_factory=dict)
    branch: str | None = None


class BaseNode:
    node_type: str
    name: str
    category: str
    description: str= ""
    config_fields: list[dict] = []
    branches: list[str] = []
    output_vars: list[dict] = []

    def run(self, ctx: NodeContext) -> NodeResult:
        raise NotImplementedError(f"{self.node_type}")

    def to_catalog(self) -> dict:
        return{
            "node_type": self.node_type,
            "name": self.name,
            "category": self.category,
            "description": self.description,
            "config_fields": self.config_fields,
            "branches": self.branches,
            "outputs": self.output_vars,
        }

    