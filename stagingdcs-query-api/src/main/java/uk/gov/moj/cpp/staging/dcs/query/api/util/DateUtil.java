package uk.gov.moj.cpp.staging.dcs.query.api.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Util class for Date conversions */
public final class DateUtil {

    private DateUtil(){

    }

    public static final DateTimeFormatter SIMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final DateTimeFormatter ZONE_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static LocalDate getLocalDateFromSimpleDateFormat(final String dateString){
        return LocalDate.parse(dateString, SIMPLE_DATE_FORMAT);
    }

}
