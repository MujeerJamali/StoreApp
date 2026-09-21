package com.mujeer.businesserp;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A month-grid calendar where each day cell is directly tappable: a single
 * tap registers one more unit on that date, a long-press removes one. Used
 * by the "Generate Entries" bulk-entry screen to let the user build up a
 * per-date quantity/amount just by tapping the days it happened on, rather
 * than filling in one date field at a time.
 */
public class CalendarTapView extends LinearLayout {

	public interface OnDateCountChangeListener {
		void onDateCountChanged(String isoDate, int newCount);
	}

	private static final String[] WEEKDAY_LABELS =
		{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

	private final Map<String, Integer> dateCounts = new HashMap<String, Integer>();
	private final View[] cellViews = new View[42];
	private final String[] cellIsoDates = new String[42];

	private final Calendar displayedMonth = Calendar.getInstance();
	private final Calendar today = Calendar.getInstance();

	private boolean allowFutureDates = false;

	private TextView tvMonth;
	private final LinearLayout[] rows = new LinearLayout[6];

	private OnDateCountChangeListener listener;

	public CalendarTapView(Context context) {
		super(context);
		init(context);
	}

	public CalendarTapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	private void init(Context context) {

		setOrientation(VERTICAL);

		// Pinned to the 1st so Calendar.add(MONTH, ...) in the nav buttons
		// can never roll over into the wrong month (e.g. Jan 31 + 1 month
		// would land on Mar 2/3, silently skipping February).
		displayedMonth.set(Calendar.DAY_OF_MONTH, 1);

		LayoutInflater.from(context).inflate(R.layout.view_calendar_tap, this, true);

		tvMonth = findViewById(R.id.tv_cal_month);

		LinearLayout weekdayRow = findViewById(R.id.row_cal_weekdays);

		rows[0] = findViewById(R.id.row_cal_0);
		rows[1] = findViewById(R.id.row_cal_1);
		rows[2] = findViewById(R.id.row_cal_2);
		rows[3] = findViewById(R.id.row_cal_3);
		rows[4] = findViewById(R.id.row_cal_4);
		rows[5] = findViewById(R.id.row_cal_5);

		int secondaryColor = getResources().getColor(R.color.text_secondary);

		for (String label : WEEKDAY_LABELS) {

			TextView tv = new TextView(context);

			tv.setLayoutParams(new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

			tv.setGravity(Gravity.CENTER);
			tv.setText(label);
			tv.setTextColor(secondaryColor);
			tv.setTextSize(12);
			tv.setTypeface(tv.getTypeface(), Typeface.BOLD);

			weekdayRow.addView(tv);
		}

		int cellIndex = 0;

		for (int r = 0; r < 6; r++) {

			for (int c = 0; c < 7; c++) {

				final int index = cellIndex;

				View cell = LayoutInflater.from(context).inflate(
					R.layout.calendar_day_cell, rows[r], false);

				cell.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onCellTapped(index, +1);
					}
				});

				cell.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						onCellTapped(index, -1);
						return true;
					}
				});

				rows[r].addView(cell);

				cellViews[cellIndex] = cell;

				cellIndex++;
			}
		}

		Button btnPrev = findViewById(R.id.btn_cal_prev);
		Button btnNext = findViewById(R.id.btn_cal_next);

		btnPrev.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				displayedMonth.add(Calendar.MONTH, -1);
				render();
			}
		});

		btnNext.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				displayedMonth.add(Calendar.MONTH, 1);
				render();
			}
		});

		render();
	}

	private void onCellTapped(int cellIndex, int delta) {

		String iso = cellIsoDates[cellIndex];

		if (iso == null) {
			return;
		}

		if (!allowFutureDates && isFuture(iso)) {
			return;
		}

		Integer current = dateCounts.get(iso);
		int currentCount = current == null ? 0 : current;

		int newCount = currentCount + delta;

		if (newCount <= 0) {
			dateCounts.remove(iso);
			newCount = 0;
		} else {
			dateCounts.put(iso, newCount);
		}

		renderCell(cellIndex);

		if (listener != null) {
			listener.onDateCountChanged(iso, newCount);
		}
	}

	private boolean isFuture(String isoDate) {
		return isoDate.compareTo(isoFormat(today)) > 0;
	}

	public void setOnDateCountChangeListener(OnDateCountChangeListener listener) {
		this.listener = listener;
	}

	public void setAllowFutureDates(boolean allow) {
		this.allowFutureDates = allow;
		render();
	}

	/**
	 * Replaces the current date/count map (e.g. when re-opening this
	 * calendar to edit a line that was already configured) and jumps the
	 * displayed month to the earliest tapped date so the existing taps are
	 * immediately visible.
	 */
	public void setDateCounts(Map<String, Integer> counts) {

		dateCounts.clear();

		if (counts != null) {
			dateCounts.putAll(counts);
		}

		String earliest = null;

		for (String iso : dateCounts.keySet()) {
			if (earliest == null || iso.compareTo(earliest) < 0) {
				earliest = iso;
			}
		}

		if (earliest != null) {

			String[] parts = earliest.split("-");

			displayedMonth.set(Calendar.YEAR, Integer.parseInt(parts[0]));
			displayedMonth.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
			displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
		}

		render();
	}

	public Map<String, Integer> getDateCounts() {
		return new LinkedHashMap<String, Integer>(dateCounts);
	}

	private static String isoFormat(Calendar cal) {
		return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
	}

	private void render() {

		tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonth.getTime()));

		Calendar cursor = (Calendar) displayedMonth.clone();
		cursor.set(Calendar.DAY_OF_MONTH, 1);

		int leading = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
		int daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH);

		for (int i = 0; i < 42; i++) {

			int dayOffset = i - leading;

			if (dayOffset >= 0 && dayOffset < daysInMonth) {

				Calendar cellDate = (Calendar) displayedMonth.clone();
				cellDate.set(Calendar.DAY_OF_MONTH, dayOffset + 1);

				cellIsoDates[i] = isoFormat(cellDate);

			} else {

				cellIsoDates[i] = null;
			}

			renderCell(i);
		}
	}

	private void renderCell(int i) {

		View cell = cellViews[i];

		TextView tvNumber = cell.findViewById(R.id.tv_day_number);
		TextView tvCount = cell.findViewById(R.id.tv_day_count);

		String iso = cellIsoDates[i];

		if (iso == null) {

			tvNumber.setText("");
			tvNumber.setBackground(null);
			tvCount.setVisibility(View.GONE);
			cell.setEnabled(false);
			cell.setClickable(false);
			cell.setLongClickable(false);

			return;
		}

		int dayOfMonth = Integer.parseInt(iso.substring(8, 10));

		tvNumber.setText(String.valueOf(dayOfMonth));

		boolean future = isFuture(iso);
		boolean disabled = future && !allowFutureDates;
		boolean isToday = iso.equals(isoFormat(today));

		Integer count = dateCounts.get(iso);
		int c = count == null ? 0 : count;

		if (disabled) {

			cell.setEnabled(false);
			cell.setClickable(false);
			cell.setLongClickable(false);

			tvNumber.setBackground(null);
			tvNumber.setTextColor(getResources().getColor(R.color.text_hint));
			tvCount.setVisibility(View.GONE);

			return;
		}

		cell.setEnabled(true);
		cell.setClickable(true);
		cell.setLongClickable(true);

		if (c > 0) {

			tvNumber.setBackgroundResource(R.drawable.bg_calendar_cell_selected);
			tvNumber.setTextColor(getResources().getColor(R.color.text_on_primary));

		} else if (isToday) {

			tvNumber.setBackgroundResource(R.drawable.bg_calendar_cell_today);
			tvNumber.setTextColor(getResources().getColor(R.color.primary));

		} else {

			tvNumber.setBackground(null);
			tvNumber.setTextColor(getResources().getColor(R.color.text_primary));
		}

		if (c > 1) {

			tvCount.setVisibility(View.VISIBLE);
			tvCount.setText(String.valueOf(c));

		} else {

			tvCount.setVisibility(View.GONE);
		}
	}
}
