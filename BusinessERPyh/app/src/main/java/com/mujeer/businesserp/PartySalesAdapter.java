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

public class PartySalesAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public PartySalesAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list) {

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

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.party_sales_row,
				parent,
				false
			);
		}

		TextView tv_party_name = convertView.findViewById(R.id.tv_party_name);
		TextView tv_party_count = convertView.findViewById(R.id.tv_party_count);
		TextView tv_party_total = convertView.findViewById(R.id.tv_party_total);

		HashMap<String, Object> row = list.get(position);

		tv_party_name.setText(
			row.get("party_name") == null ?
			"" :
			row.get("party_name").toString()
		);

		int count = Integer.parseInt(
			row.get("count").toString()
		);

		tv_party_count.setText(
			count == 1 ?
			"1 sale" :
			count + " sales"
		);

		double total = Double.parseDouble(
			row.get("total").toString()
		);

		tv_party_total.setText(
			AmountFormat.format(total)
		);

		return convertView;
	}
}
