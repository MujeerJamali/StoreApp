package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for SlowMovingStockReportActivity - each row is one
// active, in-stock item whose most recent sale (if any) falls before
// the selected cutoff, oldest/never-sold first.
public class SlowMovingStockAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public SlowMovingStockAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.slow_moving_stock_row, parent, false
			);
		}

		TextView tv_name = convertView.findViewById(R.id.tv_slow_moving_name);
		TextView tv_code = convertView.findViewById(R.id.tv_slow_moving_code);
		TextView tv_last_sale = convertView.findViewById(R.id.tv_slow_moving_last_sale);
		TextView tv_balance = convertView.findViewById(R.id.tv_slow_moving_balance);

		HashMap<String, Object> row = list.get(position);

		tv_name.setText(
			row.get("name") == null ? "" : row.get("name").toString()
		);

		tv_code.setText(
			"Code " + (row.get("code") == null ? "" : row.get("code").toString())
		);

		String lastSaleDate = (String) row.get("last_sale_date");

		tv_last_sale.setText(
			lastSaleDate == null ? "Never sold" : "Last sold " + RelativeDate.format(lastSaleDate)
		);

		double balance = row.get("balance") == null ? 0 : (Double) row.get("balance");

		tv_balance.setText(AmountFormat.formatPlain(balance));

		return convertView;
	}
}
