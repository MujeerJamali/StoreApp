package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for LowStockReportActivity - each row is one active item
// whose current stock is at or below its own reorder threshold, lowest
// stock first.
public class LowStockAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public LowStockAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.low_stock_row, parent, false
			);
		}

		TextView tv_name = convertView.findViewById(R.id.tv_low_stock_name);
		TextView tv_code = convertView.findViewById(R.id.tv_low_stock_code);
		TextView tv_threshold = convertView.findViewById(R.id.tv_low_stock_threshold);
		TextView tv_balance = convertView.findViewById(R.id.tv_low_stock_balance);

		HashMap<String, Object> row = list.get(position);

		tv_name.setText(
			row.get("name") == null ? "" : row.get("name").toString()
		);

		tv_code.setText(
			"Code " + (row.get("code") == null ? "" : row.get("code").toString())
		);

		double threshold = row.get("reorder_threshold") == null ? 0 : (Double) row.get("reorder_threshold");

		tv_threshold.setText("Reorder at " + AmountFormat.formatPlain(threshold));

		double balance = row.get("balance") == null ? 0 : (Double) row.get("balance");

		tv_balance.setText(AmountFormat.formatPlain(balance));

		return convertView;
	}
}
