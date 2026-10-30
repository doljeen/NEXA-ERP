import time

from nodes.base import BaseNode, NodeContext, NodeResult


class FileUploadNode(BaseNode):
    node_type = "file_upload"
    name = "파일 업로드"
    category = "input"
    description = "영수증·서류 파일을 받음"
    config_fields = [
        {"key": "accept", "label": "허용 형식", "type": "select",
         "options": ["이미지", "PDF", "엑셀"], "required": True},
    ]
    output_vars = [
        {"key": "files", "label": "파일 목록", "type": "list"},
    ]

    def run(self, ctx: NodeContext) -> NodeResult:
        time.sleep(1)
        files = ctx.inputs.get("files", [])
        return NodeResult(output={"files": files})