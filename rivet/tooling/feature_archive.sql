-- Read-only, consistent export of the features scheduled for retirement.
-- community_events is the realtime outbox, NOT the calendar: never export it here.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '120s';
SELECT json_build_object('format', 'rivet-feature-archive', 'version', 1);
SELECT json_build_object('table', 'documents', 'row', row_to_json(d))
FROM rivet.documents d WHERE section IN ('board', 'events', 'polls', 'ideas') ORDER BY sequence;
SELECT json_build_object('table', 'community_relations', 'row', row_to_json(r))
FROM rivet.community_relations r JOIN rivet.documents d ON d.id = r.document
WHERE d.section IN ('board', 'events', 'polls', 'ideas') ORDER BY r.document, r.kind, r.actor;
SELECT json_build_object('table', 'community_event_preferences', 'row', row_to_json(p))
FROM rivet.community_event_preferences p JOIN rivet.documents d ON d.id = p.event
WHERE d.section = 'events' ORDER BY p.event, p.player;
SELECT json_build_object('table', 'community_group_items', 'row', row_to_json(t))
FROM rivet.community_group_items t WHERE kind = 'task' ORDER BY id;
SELECT json_build_object('table', 'community_task_codes', 'row', row_to_json(c))
FROM rivet.community_task_codes c JOIN rivet.community_group_items t ON t.id = c.task_id
WHERE t.kind = 'task' ORDER BY c.task_id;
SELECT json_build_object('table', 'community_task_stocks', 'row', row_to_json(s))
FROM rivet.community_task_stocks s JOIN rivet.community_group_items t ON t.id = s.task_id
WHERE t.kind = 'task' ORDER BY s.location;
SELECT json_build_object('table', 'notices', 'row', row_to_json(n))
FROM rivet.notices n WHERE body->>'section' IN ('board', 'events', 'polls', 'ideas')
OR body->>'routeSection' = 'tasks'
OR body->>'target' IN (SELECT id FROM rivet.community_group_items WHERE kind = 'task')
OR body->>'target' IN (SELECT id FROM rivet.documents WHERE section IN ('board', 'events', 'polls', 'ideas'))
ORDER BY id;
SELECT json_build_object('complete', true);
COMMIT;
