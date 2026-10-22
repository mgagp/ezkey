#!/usr/bin/env python3
"""Minimal Integration API stub for ezkey-pam lab/CI proofs (Patrick CHANGES / #895).

Modes (EZKEY_STUB_MODE):
  ok             — POST 201; challenge only when request challengeRequested=true;
                   GET wait → ACCEPTED
  no_challenge   — POST 201 without authAttemptChallenge (even if requested)
  enrollment_challenge — POST 201 with authAttemptChallenge even when request
                   challengeRequested=false (enrollment-required; #975); wait → ACCEPTED
  slow_create    — sleep then 201 (for api_timeout)
  pending        — POST 201; GET wait sleeps for ?timeout= then PENDING + timeoutReached
  expired        — POST 201; GET wait → EXPIRED
  rejected       — POST 201; GET wait → REJECTED
  legacy_create  — ignore request ttlSeconds; response has neither timeoutSeconds nor expiresAt
  legacy_rejected — legacy create body + wait REJECTED (Patrick #897 nit)
  legacy_pending  — legacy create body + wait PENDING/timeoutReached (Patrick #897 nit)
  applied_shrink — create response timeoutSeconds = min(request, EZKEY_STUB_APPLIED_TTL)
                   (mirrors PR-A #898 applied field name; proves wait aligns down)
  applied_grow   — create response timeoutSeconds = EZKEY_STUB_TTL_CAP (120) even when
                   client asked less; PAM wait must stay at asked (Patrick #897 M1)
  applied_zero   — create response timeoutSeconds = 0 (ignored → conf wait)
  applied_null   — create response timeoutSeconds = null (ignored → conf wait)
  cancel_fail    — like expired, but POST …/cancel returns 503 (PAM outcome must not change)
  cancel_final   — like expired, but POST …/cancel returns 409 already-final
                   (PAM keeps EXPIRED; summary cancel=final — Christophe observability)

Request paths (incl. ?timeout=) and POST bodies are appended to EZKEY_STUB_REQ_LOG
when set so tests can assert ttlSeconds / timeoutSeconds and cancel (#895 PR-B / #897).
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
# applied_shrink: server returns a lower applied TTL than the client asked.
APPLIED_TTL = int(os.environ.get("EZKEY_STUB_APPLIED_TTL", "30"))

LEGACY_CREATE_MODES = frozenset(
    {"legacy_create", "legacy_rejected", "legacy_pending"}
)


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


def _parse_req_ttl(body: bytes) -> int | None:
    if not body:
        return None
    try:
        parsed = json.loads(body.decode("utf-8"))
        req_ttl = parsed.get("ttlSeconds")
        if isinstance(req_ttl, (int, float)) and not isinstance(req_ttl, bool):
            return int(req_ttl)
    except (json.JSONDecodeError, UnicodeDecodeError, TypeError, ValueError):
        return None
    return None


def _req_challenge_requested(body: bytes) -> bool:
    if not body:
        return False
    try:
        parsed = json.loads(body.decode("utf-8"))
        return bool(parsed.get("challengeRequested"))
    except (json.JSONDecodeError, UnicodeDecodeError, TypeError, ValueError):
        return False


def _include_challenge(body: bytes) -> bool:
    """Mirror Integration API: challenge when requested, forced, or legacy fixture."""
    if MODE == "no_challenge":
        return False
    if MODE == "enrollment_challenge" or MODE in LEGACY_CREATE_MODES:
        return True
    return _req_challenge_requested(body)


def _legacy_create_body() -> dict:
    # Pre-TTL server: ignore request field; no timeoutSeconds/expiresAt.
    return {
        "authAttemptId": 9001,
        "status": "PENDING",
        "authAttemptChallenge": CHALLENGE,
    }


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
            if MODE == "cancel_fail":
                # 5xx — PAM must keep its wait outcome (EXPIRED/TIMEOUT/…).
                self.send_error(503)
                return
            if MODE == "cancel_final":
                # Already final (late approve / race) — PAM pam_rc unchanged; cancel=final.
                self.send_response(409)
                self.send_header("Content-Type", "application/json")
                body409 = b'{"status":"ACCEPTED","cancelled":false}'
                self.send_header("Content-Length", str(len(body409)))
                self.end_headers()
                self.wfile.write(body409)
                return
            self._json(200, {"status": "EXPIRED", "cancelled": True})
            return

        if "/api/v1/auth-attempts" not in path or path.endswith("/wait"):
            self.send_error(404)
            return

        if MODE == "slow_create":
            time.sleep(SLOW_SECS)

        req_ttl = _parse_req_ttl(body)

        if MODE in LEGACY_CREATE_MODES:
            self._json(201, _legacy_create_body())
            return

        if MODE == "applied_null":
            resp = {
                "authAttemptId": 9001,
                "status": "PENDING",
                "timeoutSeconds": None,
                "expiresAt": "2099-01-01T00:00:00Z",
            }
            if _include_challenge(body):
                resp["authAttemptChallenge"] = CHALLENGE
            self._json(201, resp)
            return

        if MODE == "applied_zero":
            resp = {
                "authAttemptId": 9001,
                "status": "PENDING",
                "timeoutSeconds": 0,
                "expiresAt": "2099-01-01T00:00:00Z",
            }
            if _include_challenge(body):
                resp["authAttemptChallenge"] = CHALLENGE
            self._json(201, resp)
            return

        applied_ttl = 120
        if MODE == "applied_grow":
            # Server "grows" beyond PAM's ask — PAM must keep planned wait.
            applied_ttl = GLOBAL_TTL_CAP
        elif MODE == "applied_shrink":
            if req_ttl is None:
                applied_ttl = APPLIED_TTL
            else:
                applied_ttl = min(req_ttl, APPLIED_TTL, GLOBAL_TTL_CAP)
        elif req_ttl is not None:
            applied_ttl = min(req_ttl, GLOBAL_TTL_CAP)
            if applied_ttl < 15:
                # Mirror PR-A: <15 → 400 (PAM should never send this).
                self.send_error(400)
                return

        # PR-A (#898): applied duration is timeoutSeconds (request field stays ttlSeconds).
        resp = {
            "authAttemptId": 9001,
            "status": "PENDING",
            "timeoutSeconds": applied_ttl,
            "expiresAt": "2099-01-01T00:00:00Z",
        }
        if _include_challenge(body):
            resp["authAttemptChallenge"] = CHALLENGE
        self._json(201, resp)

    def do_GET(self) -> None:  # noqa: N802
        _log_req("GET", self.path)
        if "/wait" not in self.path:
            self.send_error(404)
            return
        if MODE in ("pending", "legacy_pending"):
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
        if MODE in ("expired", "cancel_fail", "cancel_final"):
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
        if MODE in ("rejected", "legacy_rejected"):
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
        # ok / enrollment_challenge / legacy_create / applied_* / no_challenge / slow_create
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
    print(
        f"stub mode={MODE} listening on :{PORT} req_log={REQ_LOG} "
        f"applied_ttl={APPLIED_TTL} ttl_cap={GLOBAL_TTL_CAP}",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
