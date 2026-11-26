import socket
import socketserver
import threading
import struct

def read_exact(s, n):
    data = b""
    while len(data) < n:
        chunk = s.recv(n-len(data))
        if not chunk:
            raise OSError("SOCKS connection closed")
        data += chunk
    return data

def pump(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass

class Relay(socketserver.BaseRequestHandler):
    def handle(self):
        try:
            with socket.create_connection(("127.0.0.1", 1055), timeout=10) as remote:
                remote.sendall(b"\x05\x01\x00")
                if read_exact(remote, 2) != b"\x05\x00":
                    raise OSError("SOCKS authentication failed")
                remote.sendall(b"\x05\x01\x00\x01" + socket.inet_aton("100.81.28.119") + struct.pack("!H", 1521))
                reply = read_exact(remote, 4)
                if reply[0] != 5 or reply[1] != 0:
                    raise OSError("SOCKS connection failed")
                sizes = {1:4, 4:16}
                n = read_exact(remote, 1)[0] if reply[3] == 3 else sizes[reply[3]]
                read_exact(remote, n+2)
                remote.settimeout(120)
                self.request.settimeout(120)
                t = threading.Thread(target=pump, args=(self.request, remote), daemon=True)
                t.start()
                pump(remote, self.request)
                t.join(timeout=125)
        except (OSError, KeyError):
            print("Oracle relay connection failed", flush=True)

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

with Server(("127.0.0.1", 11521), Relay) as server:
    print("Oracle relay listening on loopback port 11521", flush=True)
    server.serve_forever()
