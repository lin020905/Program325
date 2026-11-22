package org.xidian.program325.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public final class Inputs {
    public static final String LOCAL_DATE_TIME = "uuuu-MM-dd'T'HH:mm[:ss]";
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    private Inputs() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(BEIJING).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String text(String value, int max, String label) {
        String cleaned = trimWhitespace(value);
        if (cleaned.isEmpty()) {
            throw ApiException.invalid(label + "不能为空");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw ApiException.invalid(label + "最多允许 " + max + " 个字符");
        }
        return cleaned;
    }

    public static String trimWhitespace(String value) {
        return value == null ? "" : EDGE_WHITESPACE.matcher(value).replaceAll("");
    }

    public static void positive(long value, String label) {
        if (value <= 0) {
            throw ApiException.invalid(label + "必须为正整数");
        }
    }

    public static void reservationWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (start == null || end == null || !isHour(start) || !isHour(end)
                || !start.isAfter(now) || !end.isAfter(start) || end.getYear() > 9999
                || !start.toLocalDate().equals(end.minusSeconds(1).toLocalDate())) {
            throw ApiException.invalid("预约须从未来整点开始，选择同一天的连续整小时，可在次日 00:00 结束");
        }
    }

    public static void scheduleWindow(LocalDate from, int days) {
        if (from == null || from.getYear() < 1000 || from.getYear() > 9999
                || (days != 1 && days != 7) || from.plusDays(days).getYear() > 9999) {
            throw ApiException.invalid("排期起始日期须在数据库支持的范围内，days 只能取 1 或 7");
        }
    }

    private static boolean isHour(LocalDateTime time) {
        return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
