"""Disposable PostgreSQL + Nginx TLS/WSS integration gate. Linux and Docker Desktop."""
import argparse
import os
from pathlib import Path
import platform
import secrets
import subprocess
import time

NGINX = 'nginx@sha256:ed84151ed2d47ef93c0939eb9b9754c6990db6092cd2081e11988a47a83d47ea'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('work', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    certs = work / 'certs'
    certs.mkdir(mode=0o700, exist_ok=True)
    tag = secrets.token_hex(4)
    database, proxy = 'rivet-proxy-db-' + tag, 'rivet-proxy-' + tag
    env = os.environ.copy()
    env['POSTGRES_PASSWORD'] = secrets.token_hex(24)
    def run(command, **kwargs):
        return subprocess.run(command, check=True, **kwargs)
    try:
        with (work / 'certificates.log').open('w') as log:
            for command in [
                ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=Rivet isolated test CA', '-keyout', 'ca.key', '-out', 'ca.pem'],
                ['req', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost', '-keyout', 'server.key', '-out', 'server.csr'],
            ]:
                run(['openssl', *command], cwd=certs, stdout=log, stderr=log)
            (certs / 'extensions').write_text('subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\n')
            run(['openssl', 'x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial', '-days', '1', '-extfile', 'extensions', '-out', 'server.pem'], cwd=certs, stdout=log, stderr=log)
        linux = platform.system() == 'Linux'
        upstream = '127.0.0.1' if linux else 'host.docker.internal'
        config = work / 'nginx.conf'
        config.write_text('''events {}
http {
 access_log off;
 error_log /dev/stderr warn;
 server { listen 24484; return 308 https://localhost:24483$request_uri; }
 server {
  listen 24483 ssl;
  ssl_certificate /certs/server.pem;
  ssl_certificate_key /certs/server.key;
  ssl_protocols TLSv1.2 TLSv1.3;
  client_max_body_size 16k;
  location = /api/v1/ws {
   proxy_pass http://UPSTREAM:24482;
   proxy_http_version 1.1;
   proxy_set_header Upgrade $http_upgrade;
   proxy_set_header Connection "upgrade";
   proxy_read_timeout 75s;
   proxy_send_timeout 15s;
   proxy_buffering off;
   proxy_cache off;
  }
  location /api/v1/ {
   proxy_pass http://UPSTREAM:24481;
   proxy_http_version 1.1;
   proxy_set_header Connection "";
   proxy_buffering off;
   proxy_cache off;
  }
  location / { return 404; }
 }
}
'''.replace('UPSTREAM', upstream).replace('listen 244', 'listen 127.0.0.1:244' if linux else 'listen 244'))
        run(['docker', 'run', '-d', '--name', database, '-p', '127.0.0.1:55450:5432', '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=rivet_test', '-e', 'POSTGRES_DB=rivet_test', 'postgres:18'], env=env, stdout=subprocess.DEVNULL)
        for _ in range(60):
            if subprocess.run(['docker', 'exec', database, 'pg_isready', '-U', 'rivet_test'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('PostgreSQL not ready')
        network = ['--network', 'host'] if linux else ['-p', '127.0.0.1:24483:24483', '-p', '127.0.0.1:24484:24484']
        run(['docker', 'run', '-d', '--name', proxy, *network, '-v', str(config) + ':/etc/nginx/nginx.conf:ro', '-v', str(certs) + ':/certs:ro', NGINX], stdout=subprocess.DEVNULL)
        # nginx -t also waits for container startup and validates mounted certificate/config.
        run(['docker', 'exec', proxy, 'nginx', '-t'])
        env['RIVET_TEST_DB_PASSWORD'] = env.pop('POSTGRES_PASSWORD')
        env['RIVET_TEST_DB_HOST'], env['RIVET_TEST_DB_PORT'] = '127.0.0.1', '55450'
        def quote(value):
            return "'" + str(value).replace('\\', '\\\\').replace("'", "\\'") + "'"
        init = work / 'proxy.init.gradle'
        init.write_text('allprojects { layout.buildDirectory = file(' + quote(work / 'build') + ' + "/" + project.name) }\n' +
                       'gradle.projectsEvaluated { rootProject.project(":core") { tasks.register("apiProxySmoke", JavaExec) {\n' +
                       'dependsOn testClasses; classpath = sourceSets.test.runtimeClasspath; maxHeapSize = "512m";\n' +
                       'mainClass = "dev.abros.rivet.core.api.ApiProxyHarness"; args ' + quote(work / 'results') + ', ' + quote(certs / 'ca.pem') + '\n} } }\n')
        with (work / 'run.log').open('w') as log:
            run([str(root / 'gradlew'), '-I', str(init), ':core:apiProxySmoke', '--no-daemon', '--console=plain', '--rerun-tasks'], cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=600)
        if 'RIVET_API_PROXY_OK' not in (work / 'run.log').read_text():
            raise RuntimeError('Proxy scenario did not finish')
        print('RIVET_API_PROXY_OK')
    finally:
        with (work / 'nginx.log').open('w') as log:
            subprocess.run(['docker', 'logs', proxy], stdout=log, stderr=log)
        for name in (proxy, database):
            subprocess.run(['docker', 'rm', '-f', '-v', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # Ephemeral signing keys are never retained in CI artifacts.
        for name in ('ca.key', 'server.key'):
            (certs / name).unlink(missing_ok=True)


if __name__ == '__main__':
    main()
