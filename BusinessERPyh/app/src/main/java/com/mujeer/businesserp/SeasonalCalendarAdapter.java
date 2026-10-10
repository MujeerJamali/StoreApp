package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for SeasonalCalendarActivity - one row per manually
// marked busy-period month (see DatabaseHelper.TABLE_SEASONAL_CALENDAR).
// Reuses the generic name/detail/badge row every other small report/
// list screen in this app shares.
class SeasonalCalendarAdapter extends BaseAdapter {

	private static final String[] MONTH_NAMES = {
		"January", "February", "March", "April", "May", "June",
		"July", "August", "September", "October", "November", "December"
	};

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	SeasonalCalendarAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.list = list;
	}

	static String monthName(int month) {
		return (month >= 1 && month <= 12) ? MONTH_NAMES[month - 1] : "";
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

		View view = LayoutInflater.from(activity).inflate(
			R.layout.discount_stop_restock_row, parent, false
		);

		HashMap<String, Object> row = list.get(position);

		TextView tv_name = view.findViewById(R.id.tv_row_name);
		TextView tv_detail = view.findViewById(R.id.tv_row_detail);
		TextView tv_badge = view.findViewById(R.id.tv_row_badge);

		tv_name.setText((String) row.get("label"));

		int month = (Integer) row.get("month");
		tv_detail.setText(monthName(month));

		double multiplier = (Double) row.get("multiplier");
		int percent = (int) Math.round((multiplier - 1) * 100);

		tv_badge.setText((percent >= 0 ? "+" : "") + percent + "%");

		return view;
	}
}
