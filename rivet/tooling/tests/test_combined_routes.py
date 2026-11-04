import socket
import sys
import threading
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from combined_update import TcpRelay


class CombinedRoutes(unittest.TestCase):
    def test_tunnel_preserves_bytes_and_half_close(self):
        payload = bytes(range(256)) * 1024
        with socket.create_server(('127.0.0.1', 0)) as origin:
            origin.settimeout(5)
            errors = []
            def serve():
                try:
                    with origin.accept()[0] as client:
                        client.settimeout(5)
                        data = b''
                        while part := client.recv(65536):
                            data += part
                        if data != payload:
                            raise AssertionError('Tunnel changed request')
                        client.sendall(payload[::-1])
                except Exception as failure:
                    errors.append(failure)
            worker = threading.Thread(target=serve)
            worker.start()
            relay = TcpRelay(origin.getsockname())
            try:
                with socket.create_connection(('127.0.0.1', relay.port), timeout=5) as client:
                    client.sendall(payload)
                    client.shutdown(socket.SHUT_WR)
                    response = b''
                    while part := client.recv(65536):
                        response += part
                    self.assertEqual(payload[::-1], response)
                worker.join(5)
                self.assertFalse(worker.is_alive())
                self.assertEqual([], errors)
                self.assertEqual(2 * len(payload), relay.bytes)
            finally:
                relay.close()
                relay.close()

    def test_every_localhost_address_reaches_same_origin(self):
        addresses = list(dict.fromkeys((entry[0], entry[4][0]) for entry in
                         socket.getaddrinfo('localhost', 0, type=socket.SOCK_STREAM)))
        with socket.create_server(('127.0.0.1', 0)) as origin:
            origin.settimeout(5)
            errors = []
            def serve():
                try:
                    for _ in addresses:
                        with origin.accept()[0] as client:
                            client.sendall(b'origin')
                except Exception as failure:
                    errors.append(failure)
            worker = threading.Thread(target=serve, daemon=True)
            worker.start()
            relay = TcpRelay(origin.getsockname())
            try:
                for family, host in addresses:
                    with self.subTest(address=host), socket.socket(family, socket.SOCK_STREAM) as client:
                        client.settimeout(5)
                        client.connect((host, relay.port))
                        with client.makefile('rb') as stream:
                            self.assertEqual(b'origin', stream.read())
                worker.join(5)
                self.assertFalse(worker.is_alive())
                self.assertEqual([], errors)
            finally:
                relay.close()
