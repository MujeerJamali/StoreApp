package com.mujeer.businesserp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Frontend-only display formatting for list/transaction dates - the
// underlying stored value stays a plain "yyyy-MM-dd" string everywhere
// else (queries, sorting, editing).
public class RelativeDate {

	private static final SimpleDateFormat ISO_FORMAT =
		new SimpleDateFormat("yyyy-MM-dd", Locale.US);

	private static final SimpleDateFormat DISPLAY_FORMAT =
		new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

	// "25 Sep 2026 - Today" / "24 Sep 2026 - Yesterday" / "23 Sep 2026 -
	// 2d" for a date in the past; just the formatted date for today's
	// exact match falls under "Today" above, and a future or unparsable
	// date falls back to the formatted date (or the raw string) with no
	// suffix.
	public static String format(String isoDate) {

		if (isoDate == null || isoDate.length() < 10) {
			return String.valueOf(isoDate);
		}

		Date date;

		try {
			date = ISO_FORMAT.parse(isoDate.substring(0, 10));
		} catch (ParseException e) {
			return isoDate;
		}

		String formatted = DISPLAY_FORMAT.format(date);

		int daysAgo = daysBetween(date, new Date());

		if (daysAgo == 0) {
			return formatted + " - Today";
		}

		if (daysAgo == 1) {
			return formatted + " - Yesterday";
		}

		if (daysAgo > 1) {
			return formatted + " - " + daysAgo + "d";
		}

		return formatted;
	}

	private static int daysBetween(Date from, Date to) {

		Calendar fromCal = Calendar.getInstance();
		fromCal.setTime(from);
		clearTime(fromCal);

		Calendar toCal = Calendar.getInstance();
		toCal.setTime(to);
		clearTime(toCal);

		long diffMillis = toCal.getTimeInMillis() - fromCal.getTimeInMillis();

		return (int) (diffMillis / (24L * 60 * 60 * 1000));
	}

	private static void clearTime(Calendar cal) {
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	}
}
