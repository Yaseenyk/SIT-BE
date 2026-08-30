-- ═════════════════════════════════════════════════════════════════════════════
-- V2 — the content that was hardcoded in the single-file site.
--
-- The old page carried COMMITTEES, MEMBERS and EVENTS as JavaScript literals and
-- copied them into Firestore the first time a collection came back empty. That
-- seeding step ran in the browser, so whichever visitor loaded the page first
-- decided what everyone else saw. Here it is a migration: it runs once, on the
-- server, before the app serves a request.
--
-- Every INSERT is guarded by ON CONFLICT DO NOTHING so re-running against a
-- populated database is a no-op rather than a duplicate-key failure.
-- ═════════════════════════════════════════════════════════════════════════════

-- ── Committees ───────────────────────────────────────────────────────────────
INSERT INTO committee (id, display_order, type, name, icon, gradient, size_label, badge,
                       coord_label, coordinator, coordinator_sub, coord2_label, coordinator2)
VALUES
    ('advisory',  0, 'advisory',   'Faculty Advisory Committee',        '🎓', 'linear-gradient(135deg,#1e40af,#3b82f6)', '2 members',      'Faculty Advisory',
     'Department Head', 'Prof. A.A. Paritekar', 'CSE-AIML', 'AISA Coordinator', 'Prof. S.S. Rabade'),
    ('president', 1, 'executive',  'President',                         '👑', 'linear-gradient(135deg,#78350f,#d97706)', '1 student',      'Executive', NULL, NULL, NULL, NULL, NULL),
    ('vp',        2, 'executive',  'Vice President',                    '🤝', 'linear-gradient(135deg,#065f46,#059669)', '1 student',      'Executive', NULL, NULL, NULL, NULL, NULL),
    ('treasurer', 3, 'executive',  'Treasurer',                         '💰', 'linear-gradient(135deg,#7c2d12,#c2410c)', '1 student',      'Executive', NULL, NULL, NULL, NULL, NULL),
    ('secretary', 4, 'executive',  'General Secretary',                 '📋', 'linear-gradient(135deg,#1e3a8a,#1d4ed8)', '1 student',      'Executive', NULL, NULL, NULL, NULL, NULL),
    ('sports',    5, 'functional', 'Sports Committee',                  '🏅', 'linear-gradient(135deg,#134e4a,#0f766e)', '2-3 students',   'Functional', NULL, NULL, NULL, NULL, NULL),
    ('events',    6, 'functional', 'Event Management Committee',        '🎪', 'linear-gradient(135deg,#7f1d1d,#b91c1c)', '4-5 students',   'Functional', NULL, NULL, NULL, NULL, NULL),
    ('research',  7, 'functional', 'Research & Innovation Committee',   '🔬', 'linear-gradient(135deg,#4a044e,#7e22ce)', '3-4 students',   'Functional', NULL, NULL, NULL, NULL, NULL),
    ('technical', 8, 'functional', 'Technical Committee',               '⚙️', 'linear-gradient(135deg,#0c4a6e,#0369a1)', '4-6 students',   'Functional', NULL, NULL, NULL, NULL, NULL),
    ('media',     9, 'functional', 'Media, Design & Publicity',         '📸', 'linear-gradient(135deg,#3b0764,#6d28d9)', '3-4 students',   'Functional', NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO committee_responsibility (committee_id, position, description) VALUES
    ('advisory', 0, 'Provide academic and administrative guidance to the association'),
    ('advisory', 1, 'Approve events, workshops, seminars, and competitions'),
    ('advisory', 2, 'Ensure activities align with department and university policies'),
    ('advisory', 3, 'Support collaboration with industry experts, researchers, and alumni'),
    ('advisory', 4, 'Monitor budget usage and documentation'),
    ('advisory', 5, 'Evaluate the performance and impact of association activities'),

    ('president', 0, 'Provide overall leadership and vision for the association'),
    ('president', 1, 'Conduct regular meetings with committee members'),
    ('president', 2, 'Coordinate with faculty advisors and department administration'),
    ('president', 3, 'Represent the association in departmental meetings'),
    ('president', 4, 'Approve project proposals, events, and initiatives'),
    ('president', 5, 'Ensure smooth execution of all activities'),

    ('vp', 0, 'Assist the President in planning and executing activities'),
    ('vp', 1, 'Coordinate between different committees'),
    ('vp', 2, 'Supervise event planning and execution'),
    ('vp', 3, 'Take charge of the association in the absence of the President'),
    ('vp', 4, 'Monitor the progress of ongoing projects and events'),

    ('treasurer', 0, 'Prepare budget plans for events and activities'),
    ('treasurer', 1, 'Maintain records of income and expenditures'),
    ('treasurer', 2, 'Handle fund collection, sponsorship funds, and reimbursements'),
    ('treasurer', 3, 'Submit financial reports to faculty advisors'),
    ('treasurer', 4, 'Ensure transparency and proper use of funds'),

    ('secretary', 0, 'Maintain records, meeting minutes, and official documents'),
    ('secretary', 1, 'Prepare activity reports and event documentation'),
    ('secretary', 2, 'Manage official communication with members and faculty'),
    ('secretary', 3, 'Coordinate scheduling of meetings and activities'),
    ('secretary', 4, 'Maintain the association membership database'),

    ('sports', 0, 'Organise inter-class and inter-department sports events'),
    ('sports', 1, 'Coordinate participation in university-level sports competitions'),
    ('sports', 2, 'Promote physical wellness and team-building among AIML students'),
    ('sports', 3, 'Manage sports equipment and booking of facilities'),

    ('events', 0, 'Plan technical fests, workshops, competitions, and seminars'),
    ('events', 1, 'Prepare event schedules and logistics'),
    ('events', 2, 'Coordinate with technical, publicity, and finance teams'),
    ('events', 3, 'Manage venue arrangements, registrations, and certificates'),
    ('events', 4, 'Ensure smooth event execution'),

    ('research', 0, 'Encourage research paper writing and publications'),
    ('research', 1, 'Organize paper presentation competitions'),
    ('research', 2, 'Help students identify research problems in AIML'),
    ('research', 3, 'Conduct sessions on research methodology and tools'),
    ('research', 4, 'Support patent filing and innovation projects'),
    ('research', 5, 'Collaborate with research labs and industry'),

    ('technical', 0, 'Organize AIML workshops, coding sessions, and hackathons'),
    ('technical', 1, 'Conduct seminars on deep learning, computer vision, NLP, etc.'),
    ('technical', 2, 'Arrange guest lectures from industry experts and researchers'),
    ('technical', 3, 'Promote student research projects and innovation'),
    ('technical', 4, 'Help students participate in AI competitions and conferences'),
    ('technical', 5, 'Maintain GitHub repositories and technical resources'),

    ('media', 0, 'Design posters, banners, and promotional materials'),
    ('media', 1, 'Manage social media accounts and association website'),
    ('media', 2, 'Publicize upcoming events and achievements'),
    ('media', 3, 'Document events through photos and videos'),
    ('media', 4, 'Create newsletters and activity reports')
ON CONFLICT (committee_id, position) DO NOTHING;

-- ── Members ──────────────────────────────────────────────────────────────────
-- Placeholder names, as in the original. They are visible on the live site until
-- an admin edits them, which is intentional: an empty Structure section looks
-- broken, a bracketed placeholder looks unfinished, and unfinished is honest.
INSERT INTO member (id, name, role, committee_id, academic_year, display_order) VALUES
    (gen_random_uuid(), '[President Name]',    'President',          'president', '3rd Year', 0),
    (gen_random_uuid(), '[Vice President]',    'Vice President',     'vp',        '3rd Year', 1),
    (gen_random_uuid(), '[Treasurer]',         'Treasurer',          'treasurer', '2nd Year', 2),
    (gen_random_uuid(), '[General Secretary]', 'General Secretary',  'secretary', '2nd Year', 3),
    (gen_random_uuid(), '[Sports Head]',       'Sports Coordinator', 'sports',    '2nd Year', 4),
    (gen_random_uuid(), '[Events Head]',       'Events Head',        'events',    '3rd Year', 5),
    (gen_random_uuid(), '[Research Lead]',     'Research Lead',      'research',  '3rd Year', 6),
    (gen_random_uuid(), '[Technical Head]',    'Technical Head',     'technical', '3rd Year', 7),
    (gen_random_uuid(), '[Media Head]',        'Media Head',         'media',     '2nd Year', 8)
ON CONFLICT DO NOTHING;

-- ── Events ───────────────────────────────────────────────────────────────────
-- The old data had these split into hardcoded `upcoming` and `past` arrays. They
-- are one table now; which list an event appears in follows from starts_on.
INSERT INTO event (id, title, starts_on, ends_on, date_label, tag, emoji, description) VALUES
    (gen_random_uuid(), 'AIML Winter Symposium 2025', DATE '2025-01-18', NULL, NULL, 'Symposium', '🧠',
     'Full-day symposium with expert talks, panels, and live AI demos by students and industry professionals.'),
    (gen_random_uuid(), 'Deep Learning Workshop', DATE '2025-02-08', NULL, NULL, 'Workshop', '🔬',
     'Hands-on PyTorch workshop covering CNNs, RNNs, and Transformer architectures. Bring your laptop!'),
    (gen_random_uuid(), 'AISA Hackathon 3.0', DATE '2025-03-22', NULL, NULL, 'Hackathon', '⚡',
     '48-hour hackathon - build AI solutions to real-world problems. Rs 50,000 prize pool for winners.'),
    (gen_random_uuid(), 'Research Paper Drive', DATE '2025-04-05', NULL, NULL, 'Research', '📄',
     'Guidance sessions by Research & Innovation Committee to help students submit papers to conferences.'),
    (gen_random_uuid(), 'Introduction to Generative AI', DATE '2024-09-20', NULL, NULL, 'Lecture', '🎤',
     'Guest lecture by industry expert on LLMs, diffusion models and the future of generative AI.'),
    (gen_random_uuid(), 'Python for AI - Bootcamp', DATE '2024-10-10', DATE '2024-10-12', 'Oct 10-12, 2024', 'Bootcamp', '🐍',
     '3-day bootcamp on Python, NumPy, Pandas, and scikit-learn for students new to AIML.'),
    (gen_random_uuid(), 'Inter-College AI Quiz', DATE '2024-11-15', NULL, NULL, 'Competition', '🏆',
     'AISA hosted an AIML quiz with 12 colleges across the region participating.'),
    (gen_random_uuid(), 'OpenCV Workshop', DATE '2024-12-05', NULL, NULL, 'Workshop', '👁️',
     'Computer vision fundamentals with OpenCV and Python by Technical Committee.')
ON CONFLICT DO NOTHING;

-- ── Site settings ────────────────────────────────────────────────────────────
INSERT INTO site_settings (id, phone, email, address, about_title, about_description,
                           feature1_title, feature1_description,
                           feature2_title, feature2_description,
                           feature3_title, feature3_description,
                           feature4_title, feature4_description)
VALUES (1,
    '0231 265 8613',
    'aiml@bsiet.edu.in',
    'Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur, Maharashtra',
    'About AISA',
    'The AIML Student Association is the student body of the Department of Computer Science & Engineering (AI & ML) at Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur. We build a bridge between the classroom and the industry.',
    'Learn',       'Workshops, bootcamps and hands-on sessions across the AIML stack.',
    'Build',       'Hackathons and project teams that turn coursework into something that runs.',
    'Research',    'Paper writing, presentation practice, and guidance towards publication.',
    'Connect',     'Guest lectures, alumni talks, and industry collaboration.')
ON CONFLICT (id) DO NOTHING;
