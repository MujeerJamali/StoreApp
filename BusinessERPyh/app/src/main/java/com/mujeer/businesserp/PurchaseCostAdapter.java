package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class PurchaseCostAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public PurchaseCostAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
		return (Integer) list.get(position).get("id");
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {
			convertView = LayoutInflater.from(activity)
				.inflate(R.layout.purchase_cost_row, parent, false);
		}

		HashMap<String, Object> row = list.get(position);

		TextView tvName = convertView.findViewById(R.id.tv_pc_name);
		TextView tvDate = convertView.findViewById(R.id.tv_pc_date);
		TextView tvAmount = convertView.findViewById(R.id.tv_pc_amount);

		tvName.setText(String.valueOf(row.get("cost_item_name")));

		int purchaseCount = (Integer) row.get("purchase_count");

		tvDate.setText(
			row.get("date") + " · split across " + purchaseCount +
			(purchaseCount == 1 ? " purchase" : " purchases")
		);

		tvAmount.setText(AmountFormat.format((Double) row.get("amount")));

		return convertView;
	}
}
