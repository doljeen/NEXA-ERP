package com.ssafy.migrationservice.domain.migration.validation;

import com.ssafy.migrationservice.domain.migration.validation.issue.IssueCode;
import com.ssafy.migrationservice.domain.migration.validation.issue.Severity;
import com.ssafy.migrationservice.domain.migration.validation.schema.FieldSpec;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 값 하나의 형식 검사 (② 형식 단계). 빈 값은 여기 오지 않는다 (필수값 검사가 따로 함).
 * 고칠 수 있는 형식 차이는 FIX + 바꿀 값, 해석할 수 없으면 BLOCK.
 */
public final class FieldValueChecker {

    // 2024-01-02, 2024.1.2, 2024. 1. 2., 2024/01/02
    private static final Pattern YMD = Pattern.compile("(\\d{4})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})\\.?");
    private static final Pattern YMD_COMPACT = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern NUMBER_WITH_COMMA = Pattern.compile("-?\\d{1,3}(,\\d{3})+(\\.\\d+)?");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private FieldValueChecker() {
    }

    public record Finding(Severity severity, IssueCode code, String message, String suggestion) {
    }

    public static Optional<Finding> check(FieldSpec f, String value) {
        if (f.maxLength() != null && value.length() > f.maxLength()) {
            return block(IssueCode.TOO_LONG, Josa.eunNeun(f.label()) + " " + f.maxLength() + "자 이하여야 합니다");
        }
        return switch (f.type()) {
            case STRING -> Optional.empty();
            case DATE -> checkDate(f, value);
            case NUMBER -> checkNumber(f, value);
            case EMAIL -> EMAIL.matcher(value).matches() ? Optional.empty()
                    : block(IssueCode.INVALID_EMAIL, f.label() + " 형식이 올바르지 않습니다");
            case PHONE -> checkPhone(f, value);
            case ENUM -> checkEnum(f, value);
        };
    }

    /** 날짜로 해석되면 그 날짜, 아니면 비어 있음 (업무 규칙 단계에서 사용) */
    public static Optional<LocalDate> parseDate(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher m = YMD.matcher(value);
        if (!m.matches()) {
            m = YMD_COMPACT.matcher(value);
            if (!m.matches()) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Finding> checkDate(FieldSpec f, String value) {
        Optional<LocalDate> date = parseDate(value);
        if (date.isEmpty()) {
            return block(IssueCode.INVALID_DATE, Josa.iGa(f.label()) + " 날짜가 아닙니다");
        }
        if (ISO_DATE.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(new Finding(Severity.FIX, IssueCode.DATE_FORMAT,
                f.label() + " 날짜 형식을 YYYY-MM-DD로 통일합니다", date.get().toString()));
    }

    private static Optional<Finding> checkNumber(FieldSpec f, String value) {
        if (NUMBER.matcher(value).matches()) {
            return Optional.empty();
        }
        if (NUMBER_WITH_COMMA.matcher(value).matches()) {
            return Optional.of(new Finding(Severity.FIX, IssueCode.NUMBER_FORMAT,
                    f.label() + "의 쉼표를 제거합니다", value.replace(",", "")));
        }
        return block(IssueCode.INVALID_NUMBER, Josa.iGa(f.label()) + " 숫자가 아닙니다");
    }

    private static Optional<Finding> checkPhone(FieldSpec f, String value) {
        String digits = value.replaceAll("[\\s\\-()]", "");
        if (digits.matches("0\\d{8,10}")) {
            return Optional.empty();
        }
        return Optional.of(new Finding(Severity.WARN, IssueCode.INVALID_PHONE,
                f.label() + " 번호 형식을 확인해 주세요", null));
    }

    private static Optional<Finding> checkEnum(FieldSpec f, String value) {
        boolean ok = f.enumValues().containsKey(value)
                || f.enumValues().containsValue(value.toUpperCase(Locale.ROOT));
        if (ok) {
            return Optional.empty();
        }
        String allowed = f.enumValues().keySet().stream().sorted().collect(Collectors.joining(", "));
        return block(IssueCode.INVALID_ENUM, f.label() + "에 허용되지 않는 값입니다 (허용: " + allowed + ")");
    }

    private static Optional<Finding> block(IssueCode code, String message) {
        return Optional.of(new Finding(Severity.BLOCK, code, message, null));
    }
}
