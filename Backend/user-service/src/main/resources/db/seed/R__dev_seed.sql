-- 로컬/개발 전용 초기 데이터. dev 프로필에서만 flyway.locations에 포함된다.
-- Repeatable(R__) 마이그레이션이라 버전 순서와 무관하게 모든 V__ 뒤에 실행되며, 재실행 가능해야 한다.
-- 최초 관리자: admin@local.dev / admin1234!  (운영에는 절대 포함하지 말 것)

INSERT INTO company (company_id, name, biz_no)
VALUES (1, 'DEV COMPANY', 1234567890)
ON CONFLICT (company_id) DO NOTHING;

INSERT INTO department (dept_id, name, sort_order, company_id, parent_dept_id)
VALUES (1, '인사팀', 1, 1, NULL)
ON CONFLICT (dept_id) DO NOTHING;

INSERT INTO position (position_id, name, rank_order) VALUES (1, '사원', 1)
ON CONFLICT (position_id) DO NOTHING;

INSERT INTO grade (grade_id, name, level) VALUES (1, '관리자', 1)
ON CONFLICT (grade_id) DO NOTHING;

INSERT INTO duty (duty_id, name) VALUES (1, '팀원')
ON CONFLICT (duty_id) DO NOTHING;

INSERT INTO job_code (job_code_id, type, name, parent_id) VALUES
    (1, 'FAMILY', '경영지원', NULL),
    (2, 'KIND',   '인사',     1),
    (3, 'ROLE',   'HR 운영',  2)
ON CONFLICT (job_code_id) DO NOTHING;

INSERT INTO employee (emp_no, login_id, password_enc, name, dept_id, position_id, grade_id, duty_id,
                      emp_type, hire_type, job_family_id, job_kind_id, job_role_id, status, role)
VALUES ('DEV0001', 'admin@local.dev',
        '$2a$10$FV3KGfhQYQg73nblDCNKaeW8fCyirP10C0TRawzKL6AQZOvVfGR.u',
        '개발관리자', 1, 1, 1, 1, 'REGULAR', 'OPEN', 1, 2, 3, 'EMPLOYED', 'ADMIN')
ON CONFLICT (login_id) DO NOTHING;

-- 명시적 ID로 넣었으므로 시퀀스를 현재 최댓값 뒤로 맞춘다
SELECT setval('company_company_id_seq',   (SELECT MAX(company_id)  FROM company));
SELECT setval('department_dept_id_seq',   (SELECT MAX(dept_id)     FROM department));
SELECT setval('position_position_id_seq', (SELECT MAX(position_id) FROM position));
SELECT setval('grade_grade_id_seq',       (SELECT MAX(grade_id)    FROM grade));
SELECT setval('duty_duty_id_seq',         (SELECT MAX(duty_id)     FROM duty));
SELECT setval('job_code_job_code_id_seq', (SELECT MAX(job_code_id) FROM job_code));
