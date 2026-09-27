import json
import uuid
import urllib.request
from pathlib import Path

req = urllib.request.Request(
    "http://127.0.0.1:8080/api/v1/auth/login",
    data=json.dumps({"username": "admin", "password": "changeme"}).encode(),
    headers={"Content-Type": "application/json"},
)
with urllib.request.urlopen(req, timeout=15) as r:
    tok = json.loads(r.read().decode())["data"]["accessToken"]
    print("login ok")

sample = next(Path(r"./backend/uploads/samples").glob("sample.txt"))
boundary = "----nm" + uuid.uuid4().hex
parts = []


def add_field(name: str, value: str) -> None:
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
    parts.append(value.encode() + b"\r\n")


def add_file(name: str, filename: str, content: bytes) -> None:
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(
        f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode()
    )
    parts.append(b"Content-Type: application/octet-stream\r\n\r\n")
    parts.append(content + b"\r\n")


add_field("knowledgeBaseId", "kb_default")
add_file("file", sample.name, sample.read_bytes())
parts.append(f"--{boundary}--\r\n".encode())
body = b"".join(parts)

req = urllib.request.Request(
    "http://127.0.0.1:8080/api/v1/knowledge/documents/upload",
    data=body,
    method="POST",
    headers={
        "Authorization": "Bearer " + tok,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    },
)
try:
    with urllib.request.urlopen(req, timeout=180) as r:
        out = json.loads(r.read().decode())
        data = out.get("data") or {}
        print("upload", out.get("code"), data.get("parseStatus"), data.get("segmentCount"))
except urllib.error.HTTPError as e:
    print("upload_error", e.code, e.read().decode()[:2000])
    raise

req = urllib.request.Request(
    "http://127.0.0.1:8080/api/v1/knowledge/documents?knowledgeBaseId=kb_default",
    headers={"Authorization": "Bearer " + tok},
)
with urllib.request.urlopen(req, timeout=15) as r:
    lst = json.loads(r.read().decode())
    print("list", lst.get("code"), len(lst.get("data") or []))
