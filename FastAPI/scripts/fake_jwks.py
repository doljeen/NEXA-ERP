"""로컬 JWT 테스트용 가짜 user-service (개발용, 커밋 안 함).

1) RSA 키를 새로 만들어 JWKS를 http://127.0.0.1:18999/.well-known/jwks.json 으로 제공
2) 테스트 토큰들을 scripts/.tokens.sh 에 저장 → `source scripts/.tokens.sh`

  python scripts/fake_jwks.py
"""
import json
import time
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from jwt.algorithms import RSAAlgorithm

PORT = 18999
KID = "local-test-key"

# user-service처럼 RSA 키를 만들고 공개키만 JWKS로 공개
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
jwk = json.loads(RSAAlgorithm.to_jwk(key.public_key()))
jwk.update({"kid": KID, "use": "sig", "alg": "RS256"})
JWKS = json.dumps({"keys": [jwk]}).encode()


def make_token(sub="1001", token_type="access", exp_in=1800, signer=key, **extra) -> str:
    """user-service JwtUtil과 같은 모양의 토큰 (sub=사원ID, type, jti, exp, role, 소속)"""
    now = int(time.time())
    payload = {"sub": sub, "type": token_type, "jti": str(uuid.uuid4()), "iat": now, "exp": now + exp_in, **extra}
    return jwt.encode(payload, signer, algorithm="RS256", headers={"kid": KID})


other_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)  # 위조용 다른 키
tokens = {
    "TOKEN_OK": make_token(role="USER", deptId=10, gradeId=3, dutyId=2),
    "TOKEN_EXPIRED": make_token(exp_in=-10),
    "TOKEN_REFRESH": make_token(token_type="refresh"),
    "TOKEN_FORGED": make_token(signer=other_key),
    "TOKEN_BAD_SUB": make_token(sub="abc"),
}
out = Path(__file__).with_name(".tokens.sh")
out.write_text("".join(f"export {k}='{v}'\n" for k, v in tokens.items()), encoding="utf-8")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/.well-known/jwks.json":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(JWKS)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, fmt, *args):  # 요청 로그를 짧게
        print("JWKS 요청:", self.path)


print(f"토큰 저장: {out}  →  source {out.as_posix()}")
print(f"JWKS 제공: http://127.0.0.1:{PORT}/.well-known/jwks.json  (Ctrl+C로 종료)")
HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()