package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

// Shared list adapter for Itemrankingreportactivity and
// Partyrankingreportactivity. Each row shows: a position number, the
// entity's name, a compact breakdown of its rank (1 = highest sales)
// within Week/Month/Quarter/6 Month/9 Month/Year/All-Time, and the
// overall index those seven ranks add up to (which is what the list
// is sorted by - lower is better).
public class RankingAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;
	private final String nameKey;

	public RankingAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list,
		String nameKey) {

		this.activity = activity;
		this.list = list;
		this.nameKey = nameKey;
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

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.ranking_row,
				parent,
				false
			);
		}

		TextView tv_rank_number = convertView.findViewById(R.id.tv_rank_number);
		TextView tv_rank_name = convertView.findViewById(R.id.tv_rank_name);
		TextView tv_rank_breakdown = convertView.findViewById(R.id.tv_rank_breakdown);
		TextView tv_rank_combined = convertView.findViewById(R.id.tv_rank_combined);

		HashMap<String, Object> row = list.get(position);

		tv_rank_number.setText(String.valueOf(position + 1));

		tv_rank_name.setText(
			row.get(nameKey) == null ?
			"" :
			row.get(nameKey).toString()
		);

		int weekRank = toInt(row.get("week_rank"));
		int monthRank = toInt(row.get("month_rank"));
		int quarterRank = toInt(row.get("quarter_rank"));
		int sixMonthRank = toInt(row.get("sixmonth_rank"));
		int nineMonthRank = toInt(row.get("ninemonth_rank"));
		int yearRank = toInt(row.get("year_rank"));
		int allTimeRank = toInt(row.get("alltime_rank"));
		int overallIndex = toInt(row.get("overall_index"));

		tv_rank_breakdown.setText(
			String.format(
				Locale.getDefault(),
				"Week #%d · Month #%d · Quarter #%d · 6M #%d · 9M #%d · Year #%d · All #%d",
				weekRank, monthRank, quarterRank, sixMonthRank,
				nineMonthRank, yearRank, allTimeRank
			)
		);

		tv_rank_combined.setText(String.valueOf(overallIndex));

		return convertView;
	}

	private double toDouble(Object value) {

		if (value == null) {
			return 0;
		}

		return Double.parseDouble(value.toString());
	}

	private int toInt(Object value) {

		if (value == null) {
			return 0;
		}

		return Integer.parseInt(value.toString());
	}
}
