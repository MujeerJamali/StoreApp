package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for ComboStockReportActivity - each row is one distinct
// variety value (e.g. one Size) with its total stock across every item
// that carries it, and how many distinct items that is.
public class ComboStockAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public ComboStockAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.combo_stock_row, parent, false
			);
		}

		TextView tv_label = convertView.findViewById(R.id.tv_combo_value_label);
		TextView tv_group = convertView.findViewById(R.id.tv_combo_group_name);
		TextView tv_stock = convertView.findViewById(R.id.tv_combo_total_stock);

		HashMap<String, Object> row = list.get(position);

		String label = row.get("value_label") == null ? "" : row.get("value_label").toString();
		String groupName = row.get("group_name") == null ? "" : row.get("group_name").toString();
		int itemCount = row.get("item_count") == null ? 0 : (Integer) row.get("item_count");
		double totalStock = row.get("total_stock") == null ? 0 : (Double) row.get("total_stock");

		tv_label.setText(label);
		tv_group.setText(groupName + " · " + itemCount + (itemCount == 1 ? " item" : " items"));
		tv_stock.setText(AmountFormat.formatPlain(totalStock));

		tv_stock.setTextColor(
			activity.getResources().getColor(totalStock <= 0 ? R.color.danger : R.color.mod_items)
		);

		return convertView;
	}
}
