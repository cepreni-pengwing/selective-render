"""Check only explicitly shared logic; Minecraft API/mixin differences remain independent."""
import json
import re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads(Path(__file__).with_name("source-parity.json").read_text())
failures = []
version = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.MULTILINE).group(1).strip()
if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
    failures.append("Version must contain three numeric components: " + version)
for group, roots in [("source", ["src/client/java/de/selectiverender", "versions/mc1.21.1/src/client/java/de/selectiverender", "versions/mc26.2/src/client/java/de/selectiverender", "versions/mc26.1/src/client/java/de/selectiverender", "versions/mc26.3/src/client/java/de/selectiverender"]), ("tests", ["src/test/java", "versions/mc1.21.1/src/test/java"])]:
    for name in manifest[group]:
        paths = [ROOT / root / name for root in roots]
        if not all(path.is_file() for path in paths):
            failures.append(f"Missing shared {group}: {name}")
        elif len({path.read_bytes().replace(b"\r\n", b"\n") for path in paths}) != 1:
            failures.append(f"Divergent shared {group}: {name}")
if failures:
    raise SystemExit("\n".join(failures))
print(f"Shared-source parity passed: {len(manifest['source'])} source files and {len(manifest['tests'])} tests")
