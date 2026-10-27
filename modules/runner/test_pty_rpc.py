#!/usr/bin/env python3
"""Real Core PTY smoke on Android/Termux, isolated from installed apps and accounts.

Creates a temporary runtime/home and random loopback port. Never installs an APK
or reads production data. No model request is made. Only test-owned processes are
stopped. The bundled Core TLS certificate is trusted; no trust-all fallback.
"""
import argparse
import base64
import contextlib
import http.client
import json
import os
from pathlib import Path
import queue
import signal
import socket
import ssl
import struct
import subprocess
import sys
import tempfile
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "modules/runner"))
import prepare_runtime

class Rpc:
    def __init__(self, port, token):
        self.port, self.token = port, token
        self.tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        self.tls.check_hostname = False
        self.tls.load_verify_locations(str(ROOT / "app/src/main/res/raw/agy_core_cert.pem"))

    def connection(self):
        return http.client.HTTPSConnection("127.0.0.1", self.port,
                                          context=self.tls, timeout=8)

    def request(self, connection, method, payload, stream=False):
        data = json.dumps(payload).encode()
        if stream:
            data = b"\0" + struct.pack(">I", len(data)) + data
        connection.request("POST",
            "/exa.language_server_pb.LanguageServerService/" + method, data,
            {"Content-Type": "application/connect+json" if stream else "application/json",
             "Connect-Protocol-Version": "1", "x-codeium-csrf-token": self.token})
        response = connection.getresponse()
        if response.status != 200:
            response.close()
            raise RuntimeError("RPC " + method + " HTTP_" + str(response.status))
        return response

    def call(self, method, payload):
        c = self.connection()
        try:
            return json.loads(self.request(c, method, payload).read())
        finally:
            c.close()

class Output:
    def __init__(self, rpc, terminal):
        self.rpc, self.terminal = rpc, terminal
        self.data = queue.Queue()
        self.c = rpc.connection()
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self):
        try:
            response = self.rpc.request(self.c, "StreamTerminalOutput",
                                       {"terminalId": self.terminal}, True)
            while True:
                head = response.read(5)
                if not head:
                    break
                if len(head) != 5:
                    raise RuntimeError("Truncated Connect header")
                flag, size = head[0], struct.unpack(">I", head[1:])[0]
                if size > 8 * 1024 * 1024:
                    raise RuntimeError("Oversize Connect frame")
                payload = response.read(size)
                if len(payload) != size:
                    raise RuntimeError("Truncated Connect payload")
                message = json.loads(payload)
                if flag & 2:
                    if "error" in message:
                        raise RuntimeError("Terminal Connect stream error")
                    break
                if "output" in message:
                    self.data.put(base64.b64decode(message["output"]))
        except Exception as error:
            self.data.put(error)

    def expect(self, marker):
        accumulated = b""
        end = time.monotonic() + 12
        while time.monotonic() < end:
            try:
                item = self.data.get(timeout=max(0.01, end-time.monotonic()))
            except queue.Empty:
                break
            if isinstance(item, Exception):
                raise item
            accumulated = (accumulated + item)[-1024 * 1024:]
            if marker.encode() in accumulated:
                return
        raise RuntimeError("Missing PTY test output marker")

    def close(self):
        self.c.close()
        self.thread.join(timeout=9)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--region-bypass", action="store_true",
                        help="Test the bypass Core in the same isolated runtime")
    args = parser.parse_args()
    print("Core PTY edition:", "region-bypass" if args.region_bypass else "standard")
    if not Path("/system/bin/sh").is_file():
        raise RuntimeError("This smoke requires Android")
    if Path("/bin///sh").resolve() != Path("/system/bin/sh").resolve():
        raise RuntimeError("Incompatible Android /bin alias; do not force PTY patch")
    with tempfile.TemporaryDirectory(prefix="agy-pty-rpc-", dir=ROOT / "build") as temp:
        work = Path(temp)
        runtime = work / "runtime"
        with open(work / "prepare.log", "w") as log, contextlib.redirect_stdout(log):
            if not prepare_runtime.prepare(str(runtime), no_bypass=not args.region_bypass):
                raise RuntimeError("Isolated runtime preparation failed")
        loader = runtime / "bin/libloader.so"
        if not loader.exists():
            loader = runtime / "bin/ld-linux-aarch64.so.1"
        core = runtime / "bin/liblanguage_server.so"
        if not core.exists():
            core = runtime / "bin/language_server"
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        token = uuid.uuid4().hex
        env = dict(os.environ)
        home = work / "home"
        home.mkdir()
        env.update(HOME=str(home), TMPDIR=str(work), SHELL="/system/bin/sh",
                   TERM="xterm-256color", LANG="C.UTF-8", LC_ALL="C.UTF-8",
                   SSL_CERT_FILE=str(runtime / "certs/ca-certificates.crt"),
                   GODEBUG="netdns=go",
                   PATH=str(runtime / "bin") + ":" + os.environ.get("PATH", ""))
        for name in ("LD_PRELOAD", "LD_LIBRARY_PATH", "TERMINFO"):
            env.pop(name, None)
        cmd = [str(loader), "--library-path", str(runtime / "lib")]
        compat = runtime / "lib/liblse_compat.so"
        if compat.is_file():
            cmd += ["--preload", str(compat)]
        cmd += [str(core), "--standalone", "--override_ide_name", "antigravity",
                "--subclient_type", "hub", "--override_ide_version", "2.11.0",
                "--override_user_agent_name", "antigravity",
                "--https_server_port", str(port), "--csrf_token", token,
                "--app_data_dir", "pty-smoke-state"]
        process = None
        try:
            with open(work / "core.log", "wb") as log:
                process = subprocess.Popen(cmd, cwd=work, env=env, stdout=log,
                                           stderr=subprocess.STDOUT, start_new_session=True)
            rpc = Rpc(port, token)
            end = time.monotonic() + 40
            while True:
                if process.poll() is not None:
                    raise RuntimeError("Isolated Core exited before ready")
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.3):
                        break
                except OSError:
                    if time.monotonic() >= end:
                        raise RuntimeError("Isolated Core startup timeout")
                    time.sleep(0.2)
            scope = {"workspaceUri": work.as_uri(), "projectId": "pty-smoke-" + uuid.uuid4().hex}
            for generation in range(2):
                terminal = rpc.call("CreateTerminal", scope).get("terminal", {}).get("terminalId")
                if not terminal:
                    raise RuntimeError("Core returned no terminal")
                output = Output(rpc, terminal)
                offset = 0
                stream_id = uuid.uuid4().hex
                def send(text):
                    nonlocal offset
                    data = text.encode()
                    rpc.call("SendTerminalInput", {"terminalId": terminal,
                        "input": base64.b64encode(data).decode(),
                        "inputStreamId": stream_id, "inputOffset": str(offset)})
                    offset += len(data)
                try:
                    send("test -t 0 && test -t 1 && printf '\\nAGY_%s_OK\\n' TTY\n")
                    output.expect("AGY_TTY_OK")
                    send("read agy_probe; test \"$agy_probe\" = local-value && printf '\\nAGY_%s_OK\\n' READ\n")
                    send("local-value\n")
                    output.expect("AGY_READ_OK")
                    output.close()
                    output = Output(rpc, terminal)
                    listed = rpc.call("ListTerminals", scope).get("terminals", [])
                    if terminal not in [t.get("terminalId") for t in listed]:
                        raise RuntimeError("Terminal lost across stream reconnect")
                    send("printf '\\nAGY_%s_OK\\n' RECONNECT\n")
                    output.expect("AGY_RECONNECT_OK")
                finally:
                    rpc.call("CloseTerminal", {"terminalId": terminal, "force": True})
                    output.close()
                print("PASS real Core PTY generation", generation+1,
                      ": create, tty, interactive read/input, stream reconnect, close")
            print("PASS Core terminal recreation after close; no production/account access")
        finally:
            if process is not None:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                    process.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=5)
                except ProcessLookupError:
                    pass

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL isolated PTY smoke:", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)
