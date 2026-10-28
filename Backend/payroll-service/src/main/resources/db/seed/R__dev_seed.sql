-- 개발용 직급별 기본급. grade_id는 user-service grade.grade_id와 맞춘다.
-- TODO: 직급 체계가 확정되면 직급별 금액 추가
INSERT INTO pay_grade (grade_id, base_amount) VALUES (1, 3500000)
    ON CONFLICT (grade_id) DO NOTHING;