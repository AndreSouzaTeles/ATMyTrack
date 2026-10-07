"""Generate the supplied Pix QR and verify it with an independent decoder.

Development only:
python -m pip install --target tools/qr-runtime qrcode[pil]==8.2 zxing-cpp==3.1.1
"""
import binascii
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / "tools/qr-runtime"))
import qrcode
import zxingcpp
from PIL import Image

source = root / "app/src/main/java/com/atmytrack/app/ui/LibraryAndSupport.kt"
payload = re.search(r'private const val pix="([^"]+)"', source.read_text(encoding="utf-8")).group(1)
assert format(binascii.crc_hqx(payload[:-4].encode("utf-8"), 0xFFFF), "04X") == payload[-4:]
destination = root / "app/src/main/res/drawable-nodpi/support_pix.png"
qrcode.make(payload, box_size=8, border=4).save(destination)
decoded = zxingcpp.read_barcode(Image.open(destination))
assert decoded is not None and decoded.bytes == payload.encode("utf-8")
print("QR verified byte-for-byte; no payment performed.")
