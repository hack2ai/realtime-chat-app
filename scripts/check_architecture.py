from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java" / "com" / "chatapp"

RULES = {
    "client": r"import com\\.chatapp\\.(database|service|server)\\.",
    "socket/protocol": r"import com\\.chatapp\\.(client|server|service|database|security)\\.",
    "service": r"import com\\.chatapp\\.(client|server|socket)\\.",
    "database": r"import com\\.chatapp\\.(client|server|service|socket|security)\\.",
    "model": r"import com\\.chatapp\\.(client|server|service|database|socket|security|config)\\.",
}

for relative, pattern in RULES.items():
    directory = SRC / relative
    if not directory.exists():
        continue
    regex = re.compile(pattern)
    for path in directory.rglob("*.java"):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if regex.search(line):
                print(f"Architecture violation: {path.relative_to(ROOT)}:{line_number}: {line}", file=sys.stderr)
                raise SystemExit(1)

for path in SRC.rglob("*.java"):
    if str(path).startswith(str(SRC / "database") + "/"):
        continue
    text = path.read_text(encoding="utf-8")
    if re.search(r"import java\\.sql\\.|\\b(?:DriverManager|PreparedStatement|Statement|ResultSet)\\b", text):
        print(f"Architecture violation: JDBC usage outside database layer: {path.relative_to(ROOT)}", file=sys.stderr)
        raise SystemExit(1)

print("Architecture checks passed.")
