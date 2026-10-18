"""Sample Android separation-process PSS; this is sampled PSS, not an allocation peak."""
import argparse
import csv
import datetime
import re
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--output', required=True)
p.add_argument('--seconds', type=int, default=600)
a = p.parse_args()
start = time.monotonic()
with open(a.output, 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f)
    w.writerow(['utc', 'elapsed_seconds', 'pss_kib'])
    while time.monotonic() - start < a.seconds:
        r = subprocess.run([a.adb, 'shell', 'dumpsys', 'meminfo', 'com.atmytrack.app:separation'], capture_output=True, text=True, timeout=20)
        m = re.search(r'TOTAL PSS:\s*(\d+)', r.stdout) or re.search(r'^\s*TOTAL\s+(\d+)', r.stdout, re.M)
        if m:
            w.writerow([datetime.datetime.now(datetime.timezone.utc).isoformat(), round(time.monotonic()-start, 2), int(m[1])])
            f.flush()
        time.sleep(3)
