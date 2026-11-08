"""Validated JSONL export; no destructive import or database mutation."""
import hashlib
import json
import os
from pathlib import Path
import tempfile

TABLES = frozenset(('documents', 'community_relations', 'community_event_preferences',
                   'community_group_items', 'community_task_codes', 'community_task_stocks', 'notices'))


def validate(path):
    counts = dict.fromkeys(sorted(TABLES), 0)
    complete = False
    with path.open(encoding='utf-8') as source:
        if json.loads(next(source, '{}')) != {'format': 'rivet-feature-archive', 'version': 1}:
            raise ValueError('Unsupported feature archive')
        for line in source:
            value = json.loads(line)
            if complete:
                raise ValueError('Data after archive completion')
            if value == {'complete': True}:
                complete = True
            elif (isinstance(value, dict) and value.get('table') in TABLES
                  and isinstance(value.get('row'), dict)):
                counts[value['table']] += 1
            else:
                raise ValueError('Invalid feature archive record')
    if not complete:
        raise ValueError('Incomplete feature archive')
    return counts


def export(path, run, database):
    """Publish only a successful export, without replacing an existing path."""
    path = Path(path)
    sidecar = path.with_suffix(path.suffix + '.sha256')
    if path.exists() or path.is_symlink() or sidecar.exists() or sidecar.is_symlink():
        raise FileExistsError('Archive or checksum already exists')
    fd, pending = tempfile.mkstemp(prefix='.rivet-archive-', dir=path.parent)
    pending = Path(pending)
    try:
        with os.fdopen(fd, 'wb') as output:
            with Path(__file__).with_suffix('.sql').open('rb') as sql:
                run('psql', '--no-psqlrc', '--quiet', '--tuples-only', '--no-align',
                    '--set', 'ON_ERROR_STOP=1', '--dbname', database, stdin=sql, stdout=output)
            output.flush()
            os.fsync(output.fileno())
        counts = validate(pending)
        with pending.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        # Hard link publishes the completed file atomically and refuses overwrite.
        os.link(pending, path)
        with os.fdopen(os.open(sidecar, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as output:
            output.write(digest + '\n')
            output.flush()
            os.fsync(output.fileno())
        return counts
    finally:
        pending.unlink(missing_ok=True)
