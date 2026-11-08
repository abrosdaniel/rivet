"""Opt-in real PostgreSQL check, always using a newly created disposable database."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import uuid

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from feature_archive import validate


@unittest.skipUnless(os.getenv('RIVET_TEST_ARCHIVE_CONTAINER'), 'Requires isolated PostgreSQL container')
class FeatureArchivePostgres(unittest.TestCase):
    def test_real_export_preserves_live_data_and_excludes_retained_features(self):
        container = os.environ['RIVET_TEST_ARCHIVE_CONTAINER']
        user = os.environ.get('RIVET_TEST_ARCHIVE_USER', 'rivet_test')
        database = 'rivet_archive_test_' + uuid.uuid4().hex
        prefix = ['docker', 'exec', '-i', container]
        def sql(text, db=database):
            return subprocess.check_output([*prefix, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                                            '-U', user, '-d', db], input=text.encode())
        subprocess.run([*prefix, 'createdb', '-U', user, database], check=True)
        try:
            migrations = TOOLS.parent / 'core/src/main/resources/rivet/migrations'
            sql('CREATE SCHEMA rivet; SET search_path TO rivet;\n' +
                '\n'.join(path.read_text() for path in sorted(migrations.glob('*.sql'))))
            sql("""
                SET search_path TO rivet;
                INSERT INTO documents(id,section,body) VALUES
                    ('keep-group','groups','{"title":"Keep group"}'),
                    ('event','events','{"title":"Встреча\\nзавтра"}'),
                    ('poll','polls','{}'), ('idea','ideas','{}'), ('board','board','{}');
                INSERT INTO community_relations VALUES
                    ('keep-group','members','owner','"leader"'), ('event','participants','guest','true');
                INSERT INTO community_event_preferences VALUES ('event','guest',15);
                INSERT INTO community_group_items(id,group_id,kind,body,owner) VALUES
                    ('keep-place','keep-group','place','{"title":"Keep place"}','owner'),
                    ('group-task','keep-group','task','{"title":"Group task"}','owner'),
                    ('personal-task',NULL,'task','{"title":"Personal task"}','owner');
                INSERT INTO community_task_codes VALUES ('group-task','10000'),('personal-task','10001');
                INSERT INTO community_task_stocks VALUES ('overworld:1,2,3','group-task','{"items":{"stone":7}}');
                INSERT INTO notices(recipient,body) VALUES
                    ('guest','{"section":"events","target":"event"}'),
                    ('owner','{"section":"home","target":"personal-task"}'),
                    ('owner','{"section":"groups","target":"keep-group","routeSection":"tasks","routeId":"group-task"}'),
                    ('owner','{"section":"groups","target":"keep-group"}');
                INSERT INTO records(namespace,id,body) VALUES ('announcements','keep-announcement','{}');
                INSERT INTO auth_accounts(name,uuid,type,password) VALUES ('Owner','owner','local','secret-fixture');
                INSERT INTO community_events(topic,entity,recipient,created) VALUES ('groups','keep-group','owner',1);
            """)
            tables = ['documents', 'community_relations', 'community_event_preferences', 'community_group_items',
                      'community_task_codes', 'community_task_stocks', 'notices', 'records', 'auth_accounts', 'community_events']
            snapshot = '\n'.join(f'SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),\'[]\') FROM rivet.{table} t;' for table in tables)
            before = sql(snapshot)
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'archive.jsonl'
                subprocess.run([sys.executable, str(TOOLS / 'database_backup.py'), 'export-features',
                                '--container', container, '--database', database, '--user', user,
                                '--file', str(path)], check=True,
                               env={**os.environ, 'PGPASSWORD': os.environ.get('RIVET_TEST_DB_PASSWORD', '')})
                self.assertEqual({'documents': 4, 'community_relations': 1, 'community_event_preferences': 1,
                                  'community_group_items': 2, 'community_task_codes': 2,
                                  'community_task_stocks': 1, 'notices': 3}, validate(path))
                exported = path.read_text(encoding='utf-8')
                self.assertNotIn('secret-fixture', exported)
                self.assertNotIn('keep-place', exported)
                self.assertNotIn('keep-announcement', exported)
                documents = [v['row'] for v in map(json.loads, exported.splitlines()) if v.get('table') == 'documents']
                self.assertEqual('Встреча\nзавтра', next(v['body']['title'] for v in documents if v['id'] == 'event'))
            self.assertEqual(before, sql(snapshot), 'Export must not modify source rows')
        finally:
            subprocess.run([*prefix, 'dropdb', '-U', user, database], check=True)
