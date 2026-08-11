package uk.gov.cabinetoffice.csl.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class Utils {

    public String convertSecondsIntoDaysHoursMinutesSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long days = 0;
        if (hours > 24) {
            days = hours / 24;
            hours = hours - days * 24;
        }
        String result = "";
        if (days != 0) {
            result = result + String.format("%d", days) + " days ";
        }
        if (hours != 0) {
            result = result + String.format("%d", hours) + " hours ";
        }
        if (minutes != 0) {
            result = result + String.format("%d", minutes) + " minutes ";
        }
        if (seconds != 0) {
            result = result + String.format("%d", seconds) + " seconds";
        }
        return StringUtils.removeEnd(result, " ");
    }

    public String convertDateTimeFormat(LocalDateTime localDateTime) {
        return localDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
    }

    public String getDomainFromEmailAddress(String emailAddress) {
        return emailAddress.substring(emailAddress.indexOf('@') + 1).toLowerCase(Locale.ROOT);
    }
}
