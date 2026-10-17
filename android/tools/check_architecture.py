"""Check module direction, Android-free core and oversized source files without third-party tools."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
policy = json.loads((root / "architecture/module-boundaries.json").read_text())
errors = []
counts = {}
listed = set(policy)
modules = {str(file.parent.relative_to(root)).replace("\\", ":").replace("/", ":")
           for file in root.rglob("build.gradle.kts")
           if file.parent != root and not any(part in ("build", ".gradle") for part in file.parts)}
if modules != listed:
    errors.append(f"Unlisted/missing modules: {modules ^ listed}")
for module, allowed in policy.items():
    directory = root / module.replace(":", "/")
    actual = set(re.findall(r'project\(":([^"]+)"\)', (directory / "build.gradle.kts").read_text()))
    if not actual <= set(allowed):
        errors.append(f"{module}: forbidden dependencies {actual - set(allowed)}")
    sources = list((directory / "src/main/kotlin").rglob("*.kt"))
    counts[module] = len(sources)
    for file in sources:
        text = file.read_text(encoding="utf-8")
        if len(text.splitlines()) > 200:
            errors.append(f"{file.relative_to(root)} exceeds the 200-line review limit")
        if module in ("core:model", "core:runtime") and re.search(r'^import (android\.|androidx\.)', text, re.M):
            errors.append(f"{file.relative_to(root)} imports Android into the JVM core")
        if re.search(r'^import (com\.example\.read|dev\.folio\.study|dev\.folio\.workspace|dev\.folio\.explorer)', text, re.M) and module.startswith("core:"):
            errors.append(f"{file.relative_to(root)} imports a feature into core")
if errors:
    raise SystemExit("\n".join(errors))
print(f"Architecture checks passed: {len(policy)} modules, {sum(counts.values())} focused Kotlin source files")
for module, count in counts.items():
    print(f"  {module}: {count} files")
