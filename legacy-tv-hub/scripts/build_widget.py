#!/usr/bin/env python3
"""Build two variants of ONE Samsung Orsay widget: LAN ZIP and USB-folder ZIP."""
import json
import struct
import zlib
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
product = json.loads((ROOT / 'product.json').read_text(encoding='utf-8'))
version = product['version']
widget_id = product['orsayWidgetId']
assert product['androidApplicationId'] == 'br.com.legacytvhub.manager'
assert widget_id == 'LegacyTVHub'


def png(width, height):
    pixels = bytearray()
    for y in range(height):
        pixels.append(0)
        for x in range(width):
            edge = x < 6 or y < 6 or x >= width - 6 or y >= height - 6
            pixels.extend((22, 35, 53) if edge else (65, 163, 233))
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(pixels), 9)) + chunk(b'IEND', b'')


def archive_zip(dest, entries, prefix=''):
    with ZipFile(dest, 'w', ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = ZipInfo(prefix + name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, data)


def main():
    dest = ROOT / 'app/src/main/assets'
    dest.mkdir(parents=True, exist_ok=True)
    for old in dest.glob('LegacyTVHub_*.zip'):
        old.unlink()
    payload = {}
    for p in sorted((ROOT / 'widget').rglob('*')):
        if p.is_file():
            payload[p.relative_to(ROOT / 'widget').as_posix()] = p.read_bytes().replace(
                b'{{VERSION}}', version.encode('ascii'))
    payload['images/thumb.png'] = png(106, 76)
    payload['images/bigthumb.png'] = png(160, 108)

    network = dest / ('LegacyTVHub_' + version + '.zip')
    usb = dest / ('LegacyTVHub_USB_' + version + '.zip')
    archive_zip(network, payload)
    # Orsay USB pattern: unpack ONE fixed-named application folder at FAT32 drive root.
    archive_zip(usb, payload, prefix=widget_id + '/')
    for package, path in ((network, 'config.xml'), (usb, widget_id + '/config.xml')):
        with ZipFile(package) as z:
            cfg = z.read(path)
            assert b'<widgetname itemtype="string">Legacy TV Hub</widgetname>' in cfg
            assert version.encode('ascii') in cfg
    print('Orsay LAN widget:', network, network.stat().st_size)
    print('Orsay USB package:', usb, usb.stat().st_size)


if __name__ == '__main__':
    main()
