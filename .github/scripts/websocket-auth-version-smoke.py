#!/usr/bin/env python3
import base64
import os
import socket
import struct
import sys
import time

HOST = os.environ.get("DOFAST_WS_HOST", "127.0.0.1")
PORT = int(os.environ.get("DOFAST_WS_PORT", "8080"))
PATH = os.environ.get("DOFAST_WS_PATH", "/ws")
ORIGIN = os.environ.get("DOFAST_WS_ORIGIN", "http://localhost:5173")
TIMEOUT = float(os.environ.get("DOFAST_WS_TIMEOUT", "3"))


def recv_exact(sock, size):
    chunks = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise ConnectionError("websocket closed while reading frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def handshake():
    sock = socket.create_connection((HOST, PORT), timeout=TIMEOUT)
    sock.settimeout(TIMEOUT)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    request = (
        f"GET {PATH} HTTP/1.1\r\n"
        f"Host: {HOST}:{PORT}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Origin: {ORIGIN}\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "Sec-WebSocket-Protocol: v12.stomp\r\n"
        "\r\n"
    )
    sock.sendall(request.encode("ascii"))

    response = b""
    while b"\r\n\r\n" not in response:
        response += sock.recv(4096)
        if len(response) > 65536:
            raise RuntimeError("websocket handshake response is unexpectedly large")
    status_line = response.split(b"\r\n", 1)[0].decode("latin1", errors="replace")
    parts = status_line.split(" ", 2)
    if len(parts) < 2 or parts[1] != "101":
        sock.close()
        raise RuntimeError(f"websocket handshake failed: {status_line}")
    return sock


def send_frame(sock, opcode, payload):
    payload = payload if isinstance(payload, bytes) else payload.encode("utf-8")
    mask = os.urandom(4)
    length = len(payload)
    header = bytearray([0x80 | opcode])
    if length < 126:
        header.append(0x80 | length)
    elif length <= 0xFFFF:
        header.append(0x80 | 126)
        header.extend(struct.pack("!H", length))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack("!Q", length))
    header.extend(mask)
    masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
    sock.sendall(bytes(header) + masked)


def receive_frame(sock):
    first, second = recv_exact(sock, 2)
    opcode = first & 0x0F
    masked = bool(second & 0x80)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", recv_exact(sock, 8))[0]
    mask = recv_exact(sock, 4) if masked else None
    payload = recv_exact(sock, length)
    if mask is not None:
        payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
    return opcode, payload


def stomp_connect(token):
    sock = handshake()
    try:
        frame = (
            "CONNECT\n"
            "accept-version:1.2\n"
            "host:localhost\n"
            f"Authorization:Bearer {token}\n"
            "heart-beat:0,0\n"
            "\n\x00"
        )
        send_frame(sock, 0x1, frame)
        deadline = time.monotonic() + TIMEOUT
        while time.monotonic() < deadline:
            try:
                opcode, payload = receive_frame(sock)
            except (ConnectionError, OSError):
                return "rejected"
            except socket.timeout:
                return "timeout"

            if opcode == 0x8:
                return "rejected"
            if opcode == 0x9:
                send_frame(sock, 0xA, payload)
                continue
            if opcode != 0x1:
                continue

            text = payload.decode("utf-8", errors="replace").lstrip("\n\r")
            if text.startswith("CONNECTED"):
                return "connected"
            if text.startswith("ERROR"):
                return "rejected"
        return "timeout"
    finally:
        try:
            sock.close()
        except OSError:
            pass


def main():
    if len(sys.argv) != 3 or sys.argv[2] not in {"connected", "rejected"}:
        raise SystemExit("usage: websocket-auth-version-smoke.py <access-token> <connected|rejected>")
    actual = stomp_connect(sys.argv[1])
    expected = sys.argv[2]
    if actual != expected:
        raise RuntimeError(f"expected STOMP CONNECT outcome {expected}, got {actual}")
    print(f"STOMP CONNECT outcome: {actual}")


if __name__ == "__main__":
    main()
