ALTER TABLE community_group_items
 ADD COLUMN task_status TEXT GENERATED ALWAYS AS (CASE WHEN kind='task' THEN body->>'status' END) STORED,
 ADD COLUMN task_archived TEXT GENERATED ALWAYS AS (CASE WHEN kind='task' THEN body->>'archived' END) STORED,
 ADD COLUMN task_search TEXT GENERATED ALWAYS AS (CASE WHEN kind='task' THEN coalesce(body->>'title','') || ' ' || coalesce(body->>'description','') END) STORED;
