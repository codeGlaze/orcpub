#!/usr/bin/env python3
"""
Local proxy that injects Proxy-Authorization when forwarding to the upstream
Claude Code Web egress proxy.  This works around Maven's inability to
authenticate against proxies that use complex credentials (e.g. JWT tokens).

Source: https://github.com/anthropics/claude-code/issues/13372#issuecomment-3685454645

AGENT-ONLY -- safe to delete outside of agents/* branches.
"""

import base64
import os
import select
import socket
import threading
from urllib.parse import urlparse

LOCAL_PORT = 3128
UPSTREAM = os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY")


def get_upstream():
    p = urlparse(UPSTREAM)
    return p.hostname, p.port, p.username or "", p.password or ""


def handle(client):
    try:
        req = b""
        while b"\r\n\r\n" not in req:
            req += client.recv(4096)

        target = req.split(b"\r\n")[0].split()[1].decode()
        host, port = (target.split(":") + ["443"])[:2]

        proxy_host, proxy_port, user, pwd = get_upstream()
        auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()

        upstream = socket.socket()
        upstream.connect((proxy_host, proxy_port))
        upstream.send(
            f"CONNECT {host}:{port} HTTP/1.1\r\n"
            f"Proxy-Authorization: Basic {auth}\r\n\r\n".encode()
        )

        resp = b""
        while b"\r\n\r\n" not in resp:
            resp += upstream.recv(4096)

        if b"200" in resp.split(b"\r\n")[0]:
            client.send(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            for s in [client, upstream]:
                s.setblocking(False)
            while True:
                r, _, _ = select.select([client, upstream], [], [], 30)
                if not r:
                    break
                for s in r:
                    data = s.recv(8192)
                    if not data:
                        return
                    (upstream if s is client else client).sendall(data)
    except Exception:
        pass
    finally:
        client.close()


if __name__ == "__main__":
    if not UPSTREAM:
        print("No https_proxy / HTTPS_PROXY set -- nothing to proxy. Exiting.")
        raise SystemExit(1)

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", LOCAL_PORT))
    srv.listen(10)
    print(f"Maven auth-injection proxy listening on 127.0.0.1:{LOCAL_PORT}")
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()
