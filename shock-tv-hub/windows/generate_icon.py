#!/usr/bin/env python3
"""Generate a small Windows Vista+ multi-size ICO from the Telares vector-like emblem.

Uses only stdlib, keeping the installer's PyInstaller build reproducible.
"""
import pathlib
import struct
import zlib

COL_BG = (9, 20, 32)
COL_PANEL = (20, 40, 58)
COL_ACCENT = (104, 216, 233)


def png(size):
    rows = bytearray()
    for y in range(size):
        rows.append(0)  # PNG filter: none
        py = (y + 0.5) / size
        for x in range(size):
            px = (x + 0.5) / size
            # Rounded-screen pictogram, cyan outer frame and a centered play mark.
            inside = 0.17 < px < 0.83 and 0.19 < py < 0.81
            outer = 0.10 < px < 0.90 and 0.12 < py < 0.88
            arrow = 0.40 < px < 0.69 and abs(py - .50) < (px - .40) * 1.0
            if arrow or (outer and not inside):
                rgb = COL_ACCENT
            elif inside:
                rgb = COL_PANEL
            else:
                rgb = COL_BG
            rows.extend((*rgb, 255))

    def chunk(tag, payload):
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)

    header = struct.pack(">2I5B", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


def main():
    sizes = (16, 24, 32, 48, 64, 128, 256)
    images = [png(n) for n in sizes]
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    records = []
    for n, data in zip(sizes, images):
        records.append(struct.pack("<BBBBHHII", n if n < 256 else 0, n if n < 256 else 0, 0, 0, 1, 32, len(data), offset))
        offset += len(data)
    result = header + b"".join(records) + b"".join(images)
    path = pathlib.Path(__file__).with_name("TelaresInstall.ico")
    path.write_bytes(result)
    print("TELARES ICO:", path, len(result), "bytes; icon layers:", sizes)


if __name__ == "__main__":
    main()
