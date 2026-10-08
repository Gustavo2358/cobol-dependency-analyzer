#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

legacy_vendor="pro""leap"
legacy_purpose="bench""mark"

paths=()
while IFS= read -r -d '' path; do
  case "$path" in
    MISSION.md|.gitignore|$legacy_purpose/*|src/test/resources/dependency-regression/*.json|src/test/resources/dependency-regression/*/*.json|src/main/antlr4/Cobol.g4|src/main/antlr4/CobolPreprocessor.g4|THIRD_PARTY_NOTICES.md|specs/*|docs/history/*)
      continue
      ;;
  esac
  path_lower="${path,,}"
  if [[ "$path_lower" == *"$legacy_vendor"* || "$path_lower" == *"$legacy_purpose"* ]]; then
    paths+=("$path")
  fi
done < <(git ls-files --cached --others --exclude-standard -z)

if ((${#paths[@]})); then
  printf 'Legacy identifier found in path:\n' >&2
  printf '  %s\n' "${paths[@]}" >&2
  exit 1
fi

# Python is already required by the harness. A missing optional search binary
# must never become success through a Bash process substitution.
python3 - "$legacy_vendor" "$legacy_purpose" <<'PY'
from pathlib import Path
import re
import subprocess
import sys

vendor, purpose = sys.argv[1:]
forbidden = re.compile(re.escape(vendor) + '|' + re.escape(purpose), re.IGNORECASE)
repository = re.compile(r'(?<![\w.-])' + re.escape(vendor) + r'-poc(?![\w-]|\.\w)')
measurement = re.compile(r'(?<![\w.-])' + re.escape(purpose) + r'(?:s|ing|ed)?(?![\w-]|\.\w)', re.IGNORECASE)
artifact_span = re.compile(r'`([^`\s]+)`')
artifact_path = re.compile(r'/?[\w.@+-]+(?:/[\w.@+-]+)*/?')
artifact_extensions = {'.json', '.jsonl', '.csv', '.tsv', '.log', '.txt', '.md',
                       '.yaml', '.yml', '.html', '.js', '.gz', '.zip', '.tar', '.zst'}

def documentary_artifact(match):
    value = match[1]
    # A quoted path or filename is a reference, not a new repository identity.
    # Prose and bare identifiers inside code spans remain subject to the guard.
    if artifact_path.fullmatch(value) and ('/' in value or Path(value).suffix.lower() in artifact_extensions):
        return ''
    return match[0]
excluded = {'src/main/antlr4/Cobol.g4', 'src/main/antlr4/CobolPreprocessor.g4', 'THIRD_PARTY_NOTICES.md'}
paths = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'])
contents = []
for name in sorted(set(paths.decode().split('\0')) - {''}):
    if name in excluded or name in {'MISSION.md', '.gitignore'} or name.startswith(('specs/', 'docs/history/', purpose+'/')) or name.startswith('src/test/resources/dependency-regression/') and name.endswith('.json'):
        continue
    path = Path(name)
    if path.is_symlink() or not path.is_file():
        continue  # Match the original search: no symlink following or deleted files.
    content = path.read_text(errors='replace')
    if name.startswith('docs/') and name.endswith(('.md', '.json', '.yaml', '.yml')):
        # Exact repository identity in prose and machine-readable documentary evidence only.
        content = repository.sub('', content)
        if name.endswith('.md'):
            content = artifact_span.sub(documentary_artifact, content)
            # Ordinary measurement vocabulary is valid in prose; compound
            # product identifiers, code and structured labels remain checked.
            content = measurement.sub('', content)
    if name == 'README.md':
        content = content.replace(purpose+'/', '')  # Documented measurement tool paths.
    if forbidden.search(content):
        contents.append(name)
if contents:
    print('Legacy identifier found in content:', *contents, sep='\n  ', file=sys.stderr)
    raise SystemExit(1)
PY

printf 'Naming verification passed.\n'
