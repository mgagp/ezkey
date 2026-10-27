#!/usr/bin/env python3
"""Minimal self-signed HTTPS request counter for ezkey-pam TLS runtime guards.

Used by scripts/test-conf-security.sh (#1054): https conf without lab_http must
fail closed with zero requests handled here (proof that peer verify is active).
"""

from __future__ import annotations

import os
import ssl
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


def main() -> int:
    if len(sys.argv) != 6:
        print(
            "usage: ezkey-pam-tls-stub.py <port> <cert> <key> <req_log> <pid_file>",
            file=sys.stderr,
        )
        return 2
    port = int(sys.argv[1])
    cert, key, req_log, pid_file = sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args) -> None:  # noqa: A003
            return

        def _count(self, method: str) -> None:
            try:
                with open(req_log, "a", encoding="utf-8") as fh:
                    fh.write(f"{method} {self.path}\n")
            except OSError:
                pass

        def do_GET(self) -> None:  # noqa: N802
            self._count("GET")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")

        def do_POST(self) -> None:  # noqa: N802
            n = int(self.headers.get("Content-Length", "0"))
            if n:
                self.rfile.read(n)
            self._count("POST")
            self.send_response(201)
            self.end_headers()
            self.wfile.write(b"{}")

    httpd = HTTPServer(("127.0.0.1", port), Handler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(certfile=cert, keyfile=key)
    httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
    with open(pid_file, "w", encoding="utf-8") as fh:
        fh.write(str(os.getpid()))
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
