#!/usr/bin/env python3
"""Minimal Integration API stub for ezkey-pam lab/CI proofs (Patrick CHANGES / #895).

Modes (EZKEY_STUB_MODE):
  ok            — POST 201 with challenge 42; GET wait → ACCEPTED
  no_challenge  — POST 201 without authAttemptChallenge
  slow_create   — sleep then 201 (for api_timeout)
  pending       — POST 201; GET wait sleeps for ?timeout= then PENDING + timeoutReached
  expired       — POST 201; GET wait → EXPIRED
  rejected      — POST 201; GET wait → REJECTED

Request paths (incl. ?timeout=) and POST bodies are appended to EZKEY_STUB_REQ_LOG
when set so tests can assert ttlSeconds and cancel (#895 PR-B).
"""

from __future__ import annotations

import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

MODE = os.environ.get("EZKEY_STUB_MODE", "ok")
PORT = int(os.environ.get("EZKEY_STUB_PORT", "18080"))
SLOW_SECS = float(os.environ.get("EZKEY_STUB_SLOW_SECS", "3"))
CHALLENGE = int(os.environ.get("EZKEY_STUB_CHALLENGE", "42"))
REQ_LOG = os.environ.get("EZKEY_STUB_REQ_LOG", "/tmp/ezkey-pam-stub-reqs.log")
# Cap applied TTL like Integration API global ceiling (PR-A); PAM asks ≤90.
GLOBAL_TTL_CAP = int(os.environ.get("EZKEY_STUB_TTL_CAP", "120"))


def _log_req(method: str, path: str, body: bytes | None = None) -> None:
    try:
        with open(REQ_LOG, "a", encoding="utf-8") as fh:
            line = f"{method} {path}"
            if body:
                try:
                    line += " BODY=" + body.decode("utf-8", errors="replace")
                except Exception:
                    line += " BODY=<binary>"
            fh.write(line + "\n")
    except OSError:
        pass


def _parse_timeout(path: str) -> int:
    qs = parse_qs(urlparse(path).query)
    raw = qs.get("timeout", ["2"])[0]
    try:
        return max(1, int(raw))
    except ValueError:
        return 2


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        sys_stderr = __import__("sys").stderr
        sys_stderr.write("[stub] " + (fmt % args) + "\n")

    def _read_body(self) -> bytes:
        n = int(self.headers.get("Content-Length", "0") or "0")
        return self.rfile.read(n) if n > 0 else b""

    def _json(self, code: int, obj: dict) -> None:
        raw = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self) -> None:  # noqa: N802
        body = self._read_body()
        _log_req("POST", self.path, body)
        path = self.path.split("?", 1)[0].rstrip("/")

        if path.endswith("/cancel") and "/api/v1/auth-attempts/" in path:
            # Idempotent for clients: already-final → 409 (PAM ignores outcome).
            self._json(200, {"status": "EXPIRED", "cancelled": True})
            return

        if "/api/v1/auth-attempts" not in path or path.endswith("/wait"):
            self.send_error(404)
            return

        if MODE == "slow_create":
            time.sleep(SLOW_SECS)

        applied_ttl = 120
        if body:
            try:
                parsed = json.loads(body.decode("utf-8"))
                req_ttl = parsed.get("ttlSeconds")
                if isinstance(req_ttl, (int, float)) and not isinstance(req_ttl, bool):
                    applied_ttl = min(int(req_ttl), GLOBAL_TTL_CAP)
                    if applied_ttl < 15:
                        # Mirror PR-A: <15 → 400 (PAM should never send this).
                        self.send_error(400)
                        return
            except (json.JSONDecodeError, UnicodeDecodeError, TypeError, ValueError):
                pass

        resp = {
            "authAttemptId": 9001,
            "status": "PENDING",
            "ttlSeconds": applied_ttl,
            "expiresAt": "2099-01-01T00:00:00Z",
        }
        if MODE != "no_challenge":
            resp["authAttemptChallenge"] = CHALLENGE
        self._json(201, resp)

    def do_GET(self) -> None:  # noqa: N802
        _log_req("GET", self.path)
        if "/wait" not in self.path:
            self.send_error(404)
            return
        if MODE == "pending":
            # Match Integration wait: hold for client timeout then return PENDING.
            time.sleep(_parse_timeout(self.path))
            self._json(
                200,
                {
                    "authAttempt": 9001,
                    "status": "PENDING",
                    "completed": False,
                    "timeoutReached": True,
                    "waitDuration": _parse_timeout(self.path),
                },
            )
            return
        if MODE == "expired":
            self._json(
                200,
                {
                    "authAttempt": 9001,
                    "status": "EXPIRED",
                    "completed": True,
                    "timeoutReached": False,
                    "waitDuration": 1,
                },
            )
            return
        if MODE == "rejected":
            self._json(
                200,
                {
                    "authAttempt": 9001,
                    "status": "REJECTED",
                    "completed": True,
                    "timeoutReached": False,
                    "waitDuration": 1,
                },
            )
            return
        self._json(
            200,
            {
                "authAttempt": 9001,
                "status": "ACCEPTED",
                "completed": True,
                "timeoutReached": False,
                "waitDuration": 1,
            },
        )


def main() -> None:
    try:
        open(REQ_LOG, "w", encoding="utf-8").close()
    except OSError:
        pass
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"stub mode={MODE} listening on :{PORT} req_log={REQ_LOG}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
