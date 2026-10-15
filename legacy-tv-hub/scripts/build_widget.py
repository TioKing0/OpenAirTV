#!/usr/bin/env python3
"""Build a reproducible Samsung Orsay widget with a permanent ID."""
import json
import struct
import zlib
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
product = json.loads((ROOT / 'product.json').read_text(encoding='utf-8'))
version = product['version']
assert product['androidApplicationId'] == 'br.com.legacytvhub.manager'
assert product['orsayWidgetId'] == 'LegacyTVHub'


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


def main():
    output_dir = ROOT / 'app/src/main/assets'
    output_dir.mkdir(parents=True, exist_ok=True)
    for prior in output_dir.glob('LegacyTVHub_*.zip'):
        prior.unlink()
    package = output_dir / ('LegacyTVHub_' + version + '.zip')
    content = {}
    for path in sorted((ROOT/'widget').rglob('*')):
        if path.is_file():
            content[path.relative_to(ROOT/'widget').as_posix()] = path.read_bytes().replace(b'{{VERSION}}', version.encode('ascii'))
    content['images/thumb.png'] = png(106, 76)
    content['images/bigthumb.png'] = png(160, 108)
    with ZipFile(package, 'w', ZIP_DEFLATED) as archive:
        for name, data in sorted(content.items()):
            info = ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, data)
    with ZipFile(package) as archive:
        cfg = archive.read('config.xml')
        assert b'<widgetname itemtype="string">Legacy TV Hub</widgetname>' in cfg
        assert version.encode('ascii') in cfg
    print('Samsung widget ready:', package, package.stat().st_size, 'bytes')

if __name__ == '__main__':
    main()
