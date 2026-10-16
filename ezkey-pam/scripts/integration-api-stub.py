#!/usr/bin/env python3
"""Minimal Integration API stub for ezkey-pam lab/CI proofs (Patrick CHANGES).

Modes (EZKEY_STUB_MODE):
  ok            — POST 201 with challenge 42; GET wait → ACCEPTED
  no_challenge  — POST 201 without authAttemptChallenge
  slow_create   — sleep then 201 (for api_timeout)
  pending       — POST 201; GET wait hangs until client timeout
"""

from __future__ import annotations

import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODE = os.environ.get("EZKEY_STUB_MODE", "ok")
PORT = int(os.environ.get("EZKEY_STUB_PORT", "18080"))
SLOW_SECS = float(os.environ.get("EZKEY_STUB_SLOW_SECS", "3"))
CHALLENGE = int(os.environ.get("EZKEY_STUB_CHALLENGE", "42"))


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
        self._read_body()
        if "/api/v1/auth-attempts" not in self.path or self.path.rstrip("/").endswith(
            "/wait"
        ):
            self.send_error(404)
            return
        if MODE == "slow_create":
            time.sleep(SLOW_SECS)
        body = {
            "authAttemptId": 9001,
            "status": "PENDING",
        }
        if MODE != "no_challenge":
            body["authAttemptChallenge"] = CHALLENGE
        self._json(201, body)

    def do_GET(self) -> None:  # noqa: N802
        if "/wait" not in self.path:
            self.send_error(404)
            return
        if MODE == "pending":
            # Hold until curl client times out (wait_timeout + margin).
            time.sleep(3600)
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
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"stub mode={MODE} listening on :{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
