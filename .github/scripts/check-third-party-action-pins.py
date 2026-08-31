#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re
import sys

WORKFLOW_DIR = pathlib.Path('.github/workflows')
USES_PATTERN = re.compile(r'^\s*(?:-\s*)?uses:\s*([^\s#]+)')
IMMUTABLE_SHA = re.compile(r'^[0-9a-fA-F]{40}$')
GITHUB_OWNED_PREFIXES = ('actions/', 'github/')


def validate_reference(path: pathlib.Path, line_number: int, reference: str) -> str | None:
    if reference.startswith('./') or reference.startswith('docker://'):
        return None
    if reference.startswith(GITHUB_OWNED_PREFIXES):
        return None
    if '@' not in reference:
        return f'{path}:{line_number}: third-party action must use @<40-char-sha>: {reference}'
    action, revision = reference.rsplit('@', 1)
    if not action or not IMMUTABLE_SHA.fullmatch(revision):
        return f'{path}:{line_number}: third-party action is not pinned to an immutable 40-char SHA: {reference}'
    return None


def main() -> int:
    failures: list[str] = []
    workflow_files = sorted([*WORKFLOW_DIR.glob('*.yml'), *WORKFLOW_DIR.glob('*.yaml')])
    if not workflow_files:
        print('No GitHub Actions workflow files found', file=sys.stderr)
        return 1

    checked = 0
    for path in workflow_files:
        for line_number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
            match = USES_PATTERN.match(line)
            if not match:
                continue
            checked += 1
            failure = validate_reference(path, line_number, match.group(1))
            if failure:
                failures.append(failure)

    if failures:
        print('\n'.join(failures), file=sys.stderr)
        return 1

    print(f'GitHub Actions supply-chain contract: OK ({checked} action references checked)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
