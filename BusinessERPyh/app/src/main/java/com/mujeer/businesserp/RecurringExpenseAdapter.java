package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class RecurringExpenseAdapter extends BaseAdapter {

	private static final String[] WEEKDAY_NAMES = {
		"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
	};

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public RecurringExpenseAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.list = list;
	}

	@Override
	public int getCount() {
		return list.size();
	}

	@Override
	public Object getItem(int position) {
		return list.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {
			convertView = LayoutInflater.from(activity)
				.inflate(R.layout.recurring_expense_row, parent, false);
		}

		HashMap<String, Object> row = list.get(position);

		TextView tvItem = convertView.findViewById(R.id.tv_recurring_item);
		TextView tvSchedule = convertView.findViewById(R.id.tv_recurring_schedule);
		TextView tvAmount = convertView.findViewById(R.id.tv_recurring_amount);
		TextView tvStatus = convertView.findViewById(R.id.tv_recurring_status);

		tvItem.setText(String.valueOf(row.get("item")));
		tvSchedule.setText(describeSchedule(row));
		tvAmount.setText(AmountFormat.format((Double) row.get("amount")));

		boolean active = (Boolean) row.get("active");

		tvStatus.setText(active ? "Active" : "Paused");
		tvStatus.setTextColor(
			activity.getResources().getColor(active ? R.color.success : R.color.text_hint)
		);

		return convertView;
	}

	// Human-readable schedule summary, e.g. "Daily", "Weekly on Monday",
	// "Monthly on the 1st", "3 specific dates" - shared between the list
	// row and the edit screen's own preview.
	public static String describeSchedule(HashMap<String, Object> rule) {

		int frequency = (Integer) rule.get("frequency");

		switch (frequency) {

			case DatabaseHelper.RECURRING_DAILY:
				return "Daily";

			case DatabaseHelper.RECURRING_WEEKLY:

				Integer dayOfWeek = (Integer) rule.get("day_of_week");

				return "Weekly on " +
					(dayOfWeek != null && dayOfWeek >= 1 && dayOfWeek <= 7 ?
					WEEKDAY_NAMES[dayOfWeek] : "?");

			case DatabaseHelper.RECURRING_MONTHLY:

				Integer dayOfMonth = (Integer) rule.get("day_of_month");

				return "Monthly on the " + ordinal(dayOfMonth == null ? 1 : dayOfMonth);

			case DatabaseHelper.RECURRING_SPECIFIC_DATES:

				String specificDates = String.valueOf(rule.get("specific_dates"));
				int count = specificDates.trim().isEmpty() ?
					0 : specificDates.split(",").length;

				return count + (count == 1 ? " specific date" : " specific dates");

			default:
				return "";
		}
	}

	private static String ordinal(int day) {

		if (day % 100 >= 11 && day % 100 <= 13) {
			return day + "th";
		}

		switch (day % 10) {
			case 1: return day + "st";
			case 2: return day + "nd";
			case 3: return day + "rd";
			default: return day + "th";
		}
	}
}
