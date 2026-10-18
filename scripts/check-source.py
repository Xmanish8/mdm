"""Small source-secret check. Does not replace a history or dependency security audit."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
rules = {
    "private key": r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "AWS access key": r"\bAKIA[0-9A-Z]{16}\b",
    "GitHub token": r"\bgh[pousr]_[A-Za-z0-9]{30,}\b",
    "API token": r"\bsk-(?:proj-)?[A-Za-z0-9_-]{30,}\b",
}
findings=[]
for directory in [root/'src/main', root/'deploy']:
    for path in directory.rglob('*'):
        if not path.is_file() or path.suffix in {'.png','.ico','.jar'}: continue
        try: content=path.read_text(encoding='utf-8')
        except UnicodeError: continue
        for kind,pattern in rules.items():
            if re.search(pattern,content): findings.append((str(path.relative_to(root)),kind))
        if path.name.startswith('application') and path.suffix=='.properties':
            for line in content.splitlines():
                if line.startswith('spring.datasource.password='):
                    value=line.partition('=')[2]
                    if value and not value.startswith('${'): findings.append((str(path.relative_to(root)),'literal database password'))
for path,kind in findings: print(f'{path}: potential {kind} (value withheld)')
print(f'Source secret checks: {len(findings)} findings. Git history and runtime secret storage were not scanned.')
sys.exit(bool(findings))
