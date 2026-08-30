-- ═════════════════════════════════════════════════════════════════════════════
-- V3 — presentable demo content.
--
-- V2 seeded the structure but left the site looking half-built: the placeholder
-- member names read as "[President Name]", every seeded event was already in the
-- past, and the gallery and achievements sections were empty.
--
-- Everything here is SAMPLE DATA, written to be replaced from the admin panel.
-- The names are fictional and the gallery images are picsum.photos placeholders
-- chosen because they always resolve; swap them for real event photos.
--
-- INSERT-and-UPDATE only, never DELETE: this migration may run against a database
-- an admin has already edited, and it must not remove their work.
-- ═════════════════════════════════════════════════════════════════════════════

-- ── Members ──────────────────────────────────────────────────────────────────
-- Matched on the bracketed placeholder, so a member an admin has already renamed
-- is left untouched.
UPDATE member SET name = 'Aditi Kulkarni',  email = 'president@aisa.bsiet.in', linkedin_url = 'https://www.linkedin.com/' WHERE name = '[President Name]';
UPDATE member SET name = 'Rohan Deshmukh',  email = 'vp@aisa.bsiet.in',        linkedin_url = 'https://www.linkedin.com/' WHERE name = '[Vice President]';
UPDATE member SET name = 'Sanika Patil',    email = 'treasurer@aisa.bsiet.in'                                            WHERE name = '[Treasurer]';
UPDATE member SET name = 'Omkar Jadhav',    email = 'secretary@aisa.bsiet.in'                                            WHERE name = '[General Secretary]';
UPDATE member SET name = 'Prathamesh Shinde'                                                                             WHERE name = '[Sports Head]';
UPDATE member SET name = 'Sneha Bhosale',   email = 'events@aisa.bsiet.in'                                               WHERE name = '[Events Head]';
UPDATE member SET name = 'Aryan Chavan',    github_url = 'https://github.com/'                                           WHERE name = '[Research Lead]';
UPDATE member SET name = 'Isha Sawant',     github_url = 'https://github.com/'                                           WHERE name = '[Technical Head]';
UPDATE member SET name = 'Kaustubh Mane'                                                                                 WHERE name = '[Media Head]';

-- A few more students, so the committees with "4-6 students" do not show one.
INSERT INTO member (id, name, role, committee_id, academic_year, display_order) VALUES
    (gen_random_uuid(), 'Tanvi Gaikwad',   'Technical Member',   'technical', '2nd Year',  9),
    (gen_random_uuid(), 'Sarthak Pawar',   'Technical Member',   'technical', '3rd Year', 10),
    (gen_random_uuid(), 'Neha Salunkhe',   'Research Member',    'research',  '3rd Year', 11),
    (gen_random_uuid(), 'Vedant Kadam',    'Events Member',      'events',    '2nd Year', 12),
    (gen_random_uuid(), 'Riya Naik',       'Design Lead',        'media',     '2nd Year', 13),
    (gen_random_uuid(), 'Atharva Joshi',   'Sports Member',      'sports',    '1st Year', 14)
ON CONFLICT DO NOTHING;

-- ── Upcoming events ──────────────────────────────────────────────────────────
-- Dated relative to CURRENT_DATE so the Upcoming tab is never empty on a fresh
-- install, however long after this was written the database is first created.
-- Replace them with the real calendar; that is what the admin panel is for.
INSERT INTO event (id, title, starts_on, ends_on, tag, emoji, description, link_url) VALUES
    (gen_random_uuid(), 'Neural Networks from Scratch',
     CURRENT_DATE + 12, NULL, 'Workshop', '🧠',
     'Build a working neural network in pure NumPy before touching a framework. Backpropagation derived on the board, then written line by line. Laptops required.', NULL),

    (gen_random_uuid(), 'AISA Hackathon 4.0',
     CURRENT_DATE + 26, CURRENT_DATE + 27, 'Hackathon', '⚡',
     '36 hours, four tracks, one working prototype. Open to all years — teams of up to four. Mentors from industry on the floor through the night.', NULL),

    (gen_random_uuid(), 'Guest Lecture: LLMs in Production',
     CURRENT_DATE + 40, NULL, 'Lecture', '🎤',
     'What actually breaks when a language model meets real users — latency, cost, evaluation and the parts nobody writes papers about.', NULL),

    (gen_random_uuid(), 'Computer Vision Bootcamp',
     CURRENT_DATE + 54, CURRENT_DATE + 56, 'Bootcamp', '👁️',
     'Three days from convolutions to a deployed detector. OpenCV, PyTorch, and a final project you take home.', NULL),

    (gen_random_uuid(), 'Research Paper Writing Clinic',
     CURRENT_DATE + 68, NULL, 'Research', '📄',
     'Structure, related work, and how to survive peer review. Bring a draft or an idea; leave with an outline and a target venue.', NULL),

    (gen_random_uuid(), 'Inter-College AI Quiz 2.0',
     CURRENT_DATE + 82, NULL, 'Competition', '🏆',
     'Sixteen colleges, four rounds, one trophy. Registration opens a month before — watch this space.', NULL)
ON CONFLICT DO NOTHING;

-- ── Achievements ─────────────────────────────────────────────────────────────
INSERT INTO achievement (id, title, student, category, achieved_on, description) VALUES
    (gen_random_uuid(), 'Winner — Smart India Hackathon (Regional)', 'Isha Sawant', 'competition',
     CURRENT_DATE - 45, 'Led a four-member team to first place with a crop-disease detector running entirely on-device.'),

    (gen_random_uuid(), 'Paper accepted at ICACCI 2026', 'Aryan Chavan', 'research',
     CURRENT_DATE - 72, 'Co-authored "Lightweight Transformers for Regional Language OCR", accepted in the student track.'),

    (gen_random_uuid(), 'Machine Learning Intern, Persistent Systems', 'Rohan Deshmukh', 'internship',
     CURRENT_DATE - 110, 'Six-month internship on the recommendation platform team, converted to a pre-placement offer.'),

    (gen_random_uuid(), 'Runner-up — Kavach National Cybersecurity Hackathon', 'Sarthak Pawar', 'competition',
     CURRENT_DATE - 150, 'Second nationally for an anomaly-detection pipeline built over 48 hours.'),

    (gen_random_uuid(), 'Best Paper — State Level Technical Symposium', 'Neha Salunkhe', 'research',
     CURRENT_DATE - 190, 'Recognised for work on federated learning across low-bandwidth rural clinics.'),

    (gen_random_uuid(), 'Data Science Intern, Tata Consultancy Services', 'Tanvi Gaikwad', 'internship',
     CURRENT_DATE - 220, 'Built forecasting models for retail demand planning during a summer internship.')
ON CONFLICT DO NOTHING;

-- ── Gallery ──────────────────────────────────────────────────────────────────
-- picsum.photos with fixed seeds: deterministic, always resolves, and obviously
-- placeholder. Replace with real photographs from the events.
INSERT INTO gallery_item (id, title, description, category, taken_on, url, album_id, album_title, album_index, album_total) VALUES
    (gen_random_uuid(), 'OpenCV Workshop (1/4)', 'Hands-on session on image filtering and feature detection.', 'workshops', CURRENT_DATE - 60,
     'https://picsum.photos/seed/aisa-cv-1/1200/800', 'grp_seed_opencv', 'OpenCV Workshop', 0, 4),
    (gen_random_uuid(), 'OpenCV Workshop (2/4)', 'Hands-on session on image filtering and feature detection.', 'workshops', CURRENT_DATE - 60,
     'https://picsum.photos/seed/aisa-cv-2/1200/800', 'grp_seed_opencv', 'OpenCV Workshop', 1, 4),
    (gen_random_uuid(), 'OpenCV Workshop (3/4)', 'Hands-on session on image filtering and feature detection.', 'workshops', CURRENT_DATE - 60,
     'https://picsum.photos/seed/aisa-cv-3/1200/800', 'grp_seed_opencv', 'OpenCV Workshop', 2, 4),
    (gen_random_uuid(), 'OpenCV Workshop (4/4)', 'Hands-on session on image filtering and feature detection.', 'workshops', CURRENT_DATE - 60,
     'https://picsum.photos/seed/aisa-cv-4/1200/800', 'grp_seed_opencv', 'OpenCV Workshop', 3, 4),

    (gen_random_uuid(), 'Hackathon 3.0 (1/3)', 'Thirty-six hours, twenty-two teams.', 'events', CURRENT_DATE - 120,
     'https://picsum.photos/seed/aisa-hack-1/1200/800', 'grp_seed_hack3', 'AISA Hackathon 3.0', 0, 3),
    (gen_random_uuid(), 'Hackathon 3.0 (2/3)', 'Thirty-six hours, twenty-two teams.', 'events', CURRENT_DATE - 120,
     'https://picsum.photos/seed/aisa-hack-2/1200/800', 'grp_seed_hack3', 'AISA Hackathon 3.0', 1, 3),
    (gen_random_uuid(), 'Hackathon 3.0 (3/3)', 'Thirty-six hours, twenty-two teams.', 'events', CURRENT_DATE - 120,
     'https://picsum.photos/seed/aisa-hack-3/1200/800', 'grp_seed_hack3', 'AISA Hackathon 3.0', 2, 3),

    (gen_random_uuid(), 'Generative AI Guest Lecture', 'A packed hall for the session on diffusion models.', 'events', CURRENT_DATE - 30,
     'https://picsum.photos/seed/aisa-lecture/1200/800', NULL, NULL, NULL, NULL),
    (gen_random_uuid(), 'Inter-College AI Quiz', 'Twelve colleges, four rounds, one very close final.', 'competitions', CURRENT_DATE - 90,
     'https://picsum.photos/seed/aisa-quiz/1200/800', NULL, NULL, NULL, NULL),
    (gen_random_uuid(), 'Python Bootcamp', 'Three days of NumPy, Pandas and scikit-learn.', 'workshops', CURRENT_DATE - 200,
     'https://picsum.photos/seed/aisa-python/1200/800', NULL, NULL, NULL, NULL),
    (gen_random_uuid(), 'Project Expo', 'Final-year projects on display for the department.', 'events', CURRENT_DATE - 15,
     'https://picsum.photos/seed/aisa-expo/1200/800', NULL, NULL, NULL, NULL),
    (gen_random_uuid(), 'Robotics Demo Day', 'Line followers, arms, and one very determined hexapod.', 'competitions', CURRENT_DATE - 160,
     'https://picsum.photos/seed/aisa-robotics/1200/800', NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

-- ── Committee coordinators ───────────────────────────────────────────────────
UPDATE committee SET coord_label = 'Faculty Mentor', coordinator = 'Prof. S.S. Rabade'  WHERE id = 'technical' AND coordinator IS NULL;
UPDATE committee SET coord_label = 'Faculty Mentor', coordinator = 'Prof. A.A. Paritekar' WHERE id = 'research'  AND coordinator IS NULL;

-- ── Site copy ────────────────────────────────────────────────────────────────
UPDATE site_settings SET
    linkedin  = 'https://www.linkedin.com/',
    instagram = 'https://www.instagram.com/',
    website   = 'https://bsiet.edu.in',
    about_description =
        'AISA is the student body of the Department of Computer Science & Engineering (AI & ML) '
        || 'at Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur. We run the '
        || 'workshops, hackathons and reading groups that turn a syllabus into something you can build with — '
        || 'and we make sure every student who wants to get their hands on this work has somewhere to start.'
WHERE id = 1;
