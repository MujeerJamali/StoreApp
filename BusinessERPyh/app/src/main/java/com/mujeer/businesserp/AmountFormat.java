package com.mujeer.businesserp;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

// Shared money/quantity formatting: comma thousands separators always
// (e.g. 187,340,730,340), and no trailing ".00" when the value is a
// whole number (e.g. "500" not "500.00", but "500.50" still shows its
// decimals). Used everywhere an amount, balance or total is displayed.
public class AmountFormat {

	private static final DecimalFormat WHOLE = new DecimalFormat(
		"#,##0", DecimalFormatSymbols.getInstance(Locale.US));

	private static final DecimalFormat DECIMAL = new DecimalFormat(
		"#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

	public static String format(double value) {

		double rounded = Math.round(value * 100.0) / 100.0;

		if (rounded == Math.rint(rounded) && !Double.isInfinite(rounded)) {
			return WHOLE.format(rounded);
		}

		return DECIMAL.format(rounded);
	}

	// Same "no trailing .00 when whole" rule, but no comma grouping - for
	// EditText fields the user (or code) will Double.parseDouble() back
	// out again, where a "1,234.50" would throw.
	public static String formatPlain(double value) {

		double rounded = Math.round(value * 100.0) / 100.0;

		if (rounded == Math.rint(rounded) && !Double.isInfinite(rounded)) {
			return String.valueOf((long) rounded);
		}

		return String.valueOf(rounded);
	}
}
