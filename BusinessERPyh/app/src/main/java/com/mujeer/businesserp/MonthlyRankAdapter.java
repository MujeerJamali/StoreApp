package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class MonthlyRankAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public MonthlyRankAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				.inflate(R.layout.monthly_rank_row, parent, false);
		}

		HashMap<String, Object> row = list.get(position);

		TextView tvNumber = convertView.findViewById(R.id.tv_rank_number);
		TextView tvName = convertView.findViewById(R.id.tv_rank_name);
		TextView tvMonths = convertView.findViewById(R.id.tv_rank_months);
		TextView tvSum = convertView.findViewById(R.id.tv_rank_sum);

		tvNumber.setText(String.valueOf(position + 1));
		tvName.setText(String.valueOf(row.get("item_name")));

		int monthsCounted = (Integer) row.get("months_counted");

		tvMonths.setText(
			"Ranked in " + monthsCounted + (monthsCounted == 1 ? " month" : " months")
		);

		tvSum.setText(String.valueOf(row.get("rank_sum")));

		return convertView;
	}
}
