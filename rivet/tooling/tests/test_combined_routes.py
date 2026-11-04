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
