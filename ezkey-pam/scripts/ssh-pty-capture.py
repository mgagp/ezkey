#!/usr/bin/env python3
"""Run ssh under a PTY, tee output live, accept confirm via a control fifo.

OpenSSH keyboard-interactive reads prompts from the local PTY, not from a
redirected stdin pipe — so tests that need to answer the pre-wait prompt
must drive a real PTY (Patrick challenge-before-approve).

Control commands (write to the fifo):
  ok     — type the required confirmation token then Enter (2.2.12 / #975 D4)
  enter  — bare Enter only (empty response; used for negative tests)
  quit   — SIGTERM the ssh child
  intr   — SIGINT the ssh child (Ctrl-C at the prompt)
"""
from __future__ import annotations

import errno
import os
import pty
import select
import sys
import time


def _reap(pid: int) -> int:
    _wpid, wstatus = os.waitpid(pid, 0)
    return os.waitstatus_to_exitcode(wstatus)


def main() -> int:
    if len(sys.argv) < 4:
        print(
            "usage: ssh-pty-capture.py <log> <control-fifo> <ssh> [args...]",
            file=sys.stderr,
        )
        return 2
    log_path = sys.argv[1]
    ctrl_path = sys.argv[2]
    ssh_argv = sys.argv[3:]

    if not os.path.exists(ctrl_path):
        os.mkfifo(ctrl_path)

    pid, master = pty.fork()
    if pid == 0:
        os.execvp(ssh_argv[0], ssh_argv)
        os._exit(127)

    # RDWR so the fifo stays open across short-lived writers (printf >fifo).
    ctrl_fd = os.open(ctrl_path, os.O_RDWR | os.O_NONBLOCK)
    # Owner-only: capture may include challenge digits (CodeQL py/overly-permissive-file).
    log_fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)

    reaped = False
    status = 1
    try:
        while not reaped:
            try:
                readable, _, _ = select.select([master, ctrl_fd], [], [], 0.25)
            except (InterruptedError, select.error):
                continue

            if master in readable:
                try:
                    chunk = os.read(master, 4096)
                except OSError as exc:
                    if exc.errno == errno.EIO:
                        status = _reap(pid)
                        reaped = True
                        chunk = b""
                    else:
                        raise
                if chunk:
                    os.write(log_fd, chunk)
                    try:
                        os.write(sys.stdout.fileno(), chunk)
                    except OSError:
                        pass
                elif not reaped:
                    status = _reap(pid)
                    reaped = True

            if reaped:
                break

            if ctrl_fd in readable:
                try:
                    cmd = os.read(ctrl_fd, 256)
                except OSError as exc:
                    if exc.errno in (errno.EAGAIN, errno.EWOULDBLOCK):
                        cmd = b""
                    else:
                        raise
                if cmd:
                    lower = cmd.lower()
                    # Prefer "ok" over bare enter — module requires explicit confirm.
                    if b"ok" in lower:
                        os.write(master, b"ok\n")
                    elif b"enter" in lower:
                        os.write(master, b"\n")
                    if b"intr" in lower:
                        try:
                            os.kill(pid, 2)  # SIGINT — Ctrl-C
                        except OSError:
                            pass
                    elif b"quit" in lower or b"exit" in lower:
                        try:
                            os.kill(pid, 15)
                        except OSError:
                            pass

            wpid, wstatus = os.waitpid(pid, os.WNOHANG)
            if wpid == pid:
                status = os.waitstatus_to_exitcode(wstatus)
                reaped = True
                deadline = time.time() + 0.5
                while time.time() < deadline:
                    r, _, _ = select.select([master], [], [], 0.05)
                    if master not in r:
                        break
                    try:
                        chunk = os.read(master, 4096)
                    except OSError:
                        break
                    if not chunk:
                        break
                    os.write(log_fd, chunk)
    finally:
        if not reaped:
            try:
                status = _reap(pid)
                reaped = True
            except ChildProcessError:
                pass
        for fd in (ctrl_fd, log_fd, master):
            try:
                os.close(fd)
            except OSError:
                pass

    return status


if __name__ == "__main__":
    sys.exit(main())
