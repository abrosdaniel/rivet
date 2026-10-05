-- Representative persisted v5 skin library, before the position column existed.
INSERT INTO skin_images(hash,png) VALUES ('legacy-a',decode('00','hex')),('legacy-b',decode('01','hex'));
INSERT INTO skin_library(id,owner,hash,name,slim) VALUES
('00000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','legacy-a','Builder',false),
('00000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','legacy-b','Explorer',true);
INSERT INTO skin_profiles(owner,active,slim,source) VALUES
('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',true,'library');
