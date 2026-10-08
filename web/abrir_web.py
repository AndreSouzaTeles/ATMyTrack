import argparse
import os
import pathlib
import subprocess
import sys
import time
import urllib.request
import webbrowser

URL = 'http://127.0.0.1:4173/'
ROOT = pathlib.Path(__file__).resolve().parent / 'dist'

def ready():
    try:
        with urllib.request.urlopen(URL, timeout=1) as response:
            return b'ATMyTrack' in response.read(16384)
    except Exception:
        return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    if not (ROOT / 'index.html').is_file():
        print('Pasta do site nao encontrada: ' + str(ROOT))
        return 1
    if not ready():
        log_path = pathlib.Path(os.environ.get('LOCALAPPDATA', str(pathlib.Path.home()))) / 'ATMyTrack' / 'WebShortcut' / 'servidor.log'
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open('ab') as log:
            server = subprocess.Popen(
                [sys.executable, '-m', 'http.server', '4173', '--bind', '127.0.0.1', '--directory', str(ROOT)],
                stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
        for _ in range(40):
            if ready():
                break
            if server.poll() is not None:
                break
            time.sleep(0.25)
        if not ready():
            if server.poll() is None:
                server.terminate()
            print('Nao foi possivel iniciar o site. Verifique se a porta 4173 esta livre.')
            print('Detalhes: ' + str(log_path))
            return 1
    print('ATMyTrack pronto: ' + URL)
    if not args.no_browser:
        webbrowser.open(URL, new=2)
    return 0

if __name__ == '__main__':
    sys.exit(main())
