#!/usr/bin/env python3
import base64
import os
import socket
import sys
import time

HOST = os.environ.get("DOFAST_WEB_HOST", "127.0.0.1")
PORT = int(os.environ.get("DOFAST_WEB_PORT", "5173"))
ORIGIN = os.environ.get("DOFAST_WS_ORIGIN", "http://localhost:5173")


def handshake(valid_key=True, timeout=3.0):
    sock = socket.create_connection((HOST, PORT), timeout=timeout)
    sock.settimeout(timeout)
    key = base64.b64encode(os.urandom(16)).decode() if valid_key else "invalid"
    request = (
        "GET /ws HTTP/1.1\r\n"
        f"Host: localhost:{PORT}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Origin: {ORIGIN}\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    sock.sendall(request.encode("ascii"))
    response = sock.recv(4096).decode("latin1", errors="replace")
    status_line = response.split("\r\n", 1)[0]
    parts = status_line.split(" ", 2)
    if len(parts) < 2 or not parts[1].isdigit():
        sock.close()
        raise RuntimeError(f"unexpected HTTP response: {status_line!r}")
    return int(parts[1]), sock


held = []
try:
    # Eight native WebSocket connections from one peer are allowed.
    for index in range(8):
        status, sock = handshake(valid_key=True)
        if status != 101:
            sock.close()
            raise RuntimeError(f"connection {index + 1}: expected 101, got {status}")
        held.append(sock)

    # The ninth concurrent connection must be rejected at the gateway before Spring/STOMP.
    status, sock = handshake(valid_key=True)
    sock.close()
    if status != 429:
        raise RuntimeError(f"expected ninth concurrent WebSocket connection to return 429, got {status}")
finally:
    for sock in held:
        try:
            sock.close()
        except OSError:
            pass

# Let the request-rate token bucket replenish after the connection-cap probe.
time.sleep(3)

saw_429 = False
for _ in range(30):
    status, sock = handshake(valid_key=False)
    sock.close()
    if status == 429:
        saw_429 = True
        break

if not saw_429:
    raise RuntimeError("rapid WebSocket handshake burst never hit the configured 429 rate limit")

print("WebSocket ingress connection and handshake limits: OK")
