"""Generate an original, disposable 1.20.1 resourcepack for CTM integration tests."""
import json
import struct
import sys
import zlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED


def png(rgb):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    pixel = bytes((*rgb, 255))
    rows = b''.join(b'\0' + pixel * 16 for _ in range(16))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


run = Path(sys.argv[1])
packs = run / 'resourcepacks'
packs.mkdir(parents=True, exist_ok=True)
with ZipFile(packs / 'SR-boundary-smoke.zip', 'w', ZIP_DEFLATED) as pack:
    pack.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': 15, 'description': 'SR disposable boundary model/CTM smoke fixture'}}))
    pack.writestr('assets/minecraft/textures/block/stone.png', png((204, 51, 170)))
    prefix = 'assets/minecraft/optifine/ctm/sr_boundary_smoke/'
    pack.writestr(prefix + 'stone.properties', 'matchBlocks=minecraft:stone\nmethod=ctm\ntiles=0-46\n')
    for tile in range(47):
        pack.writestr(prefix + str(tile) + '.png', png((32, 204, 112)))
options = run / 'options.txt'
lines = options.read_text(encoding='utf-8').splitlines() if options.exists() else []
lines = [line for line in lines if not line.startswith('resourcePacks:')]
lines.append('resourcePacks:["vanilla","file/SR-boundary-smoke.zip"]')
options.write_text('\n'.join(lines) + '\n', encoding='utf-8')
