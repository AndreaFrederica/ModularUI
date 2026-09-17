"""Local, read-only MUI resource server. Python 3.10+, no third-party packages."""
import argparse
import hashlib
import json
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
import webbrowser

HERE = Path(__file__).resolve().parent


def snapshot(root):
    files = {}
    for path in sorted(root.rglob('*')):
        if path.suffix not in ('.xml', '.css', '.json') or not path.is_file():
            continue
        if not path.resolve().is_relative_to(root.resolve()):
            raise ValueError('Resource symlink escapes root: ' + str(path))
        if path.stat().st_size > 2_000_000:
            raise ValueError('Resource exceeds 2 MB: ' + str(path))
        files[path.relative_to(root).as_posix()] = path.read_text(encoding='utf-8-sig')
    encoded = json.dumps(files, ensure_ascii=False, sort_keys=True).encode('utf-8')
    if len(encoded) > 20_000_000:
        raise ValueError('Resource bundle exceeds 20 MB')
    return {'revision': hashlib.sha256(encoded).hexdigest(), 'files': files}


def handler_for(root):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            # No file writes, arbitrary paths, or remote resource proxying.
            if self.headers.get('Host') not in (f'127.0.0.1:{self.server.server_port}',
                                                f'localhost:{self.server.server_port}'):
                self.send_error(403)
                return
            path = urlsplit(self.path).path
            try:
                if path == '/api/snapshot':
                    bundle = snapshot(root)
                    etag = '"' + bundle['revision'] + '"'
                    if self.headers.get('If-None-Match') == etag:
                        self.send_response(304)
                        self.end_headers()
                        return
                    data = json.dumps(bundle, ensure_ascii=False).encode('utf-8')
                    kind = 'application/json; charset=utf-8'
                elif path in ('/', '/app.js', '/engine.js', '/showcase.js', '/style.css'):
                    name = 'index.html' if path == '/' else path[1:]
                    data = (HERE / name).read_bytes()
                    kind = {'html': 'text/html', 'js': 'text/javascript', 'css': 'text/css'}[name.rsplit('.', 1)[1]] + '; charset=utf-8'
                    etag = None
                else:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header('Content-Type', kind)
                self.send_header('Content-Length', str(len(data)))
                self.send_header('Cache-Control', 'no-cache')
                self.send_header('X-Content-Type-Options', 'nosniff')
                self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'none'; connect-src 'self'; frame-src 'self'; object-src 'none'; base-uri 'none'")
                if etag:
                    self.send_header('ETag', etag)
                self.end_headers()
                self.wfile.write(data)
            except (OSError, ValueError) as error:
                data = json.dumps({'error': str(error)}).encode()
                self.send_response(422)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(data)

        def log_message(self, *_):
            pass
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path, help='Directory containing mui-app.json (resources/assets/<modid>/mui)')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    root = args.root.resolve()
    if not (root / 'mui-app.json').is_file():
        parser.error('root must contain mui-app.json')
    server = ThreadingHTTPServer(('127.0.0.1', args.port), handler_for(root))
    url = f'http://127.0.0.1:{server.server_port}'
    print(f'MUI XML Preview: {url}\nResources: {root}\nCtrl+C to stop.', flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
