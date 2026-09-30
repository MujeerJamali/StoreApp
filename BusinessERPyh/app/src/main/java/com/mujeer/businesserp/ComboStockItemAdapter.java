package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for ComboStockItemsActivity - each row is one item that
// carries the selected variety value, showing its code in a colored
// badge, its plain name, its stock at that one value, and a Spinner of
// every combo it has (all its sizes, not just the ones with stock -
// see DatabaseHelper.getItemsForVarietyValue()).
public class ComboStockItemAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public ComboStockItemAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
	@SuppressWarnings("unchecked")
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.combo_stock_item_row, parent, false
			);
		}

		TextView tv_code = convertView.findViewById(R.id.tv_item_code_badge);
		TextView tv_name = convertView.findViewById(R.id.tv_item_name);
		TextView tv_stock = convertView.findViewById(R.id.tv_item_stock_at_value);
		Spinner spinner = convertView.findViewById(R.id.spinner_item_combos);

		HashMap<String, Object> row = list.get(position);

		tv_code.setText(row.get("code") == null ? "" : row.get("code").toString());
		tv_name.setText(row.get("name") == null ? "" : row.get("name").toString());

		double stockAtValue = row.get("stock_at_value") == null ? 0 : (Double) row.get("stock_at_value");

		tv_stock.setText(AmountFormat.formatPlain(stockAtValue));

		ArrayList<HashMap<String, Object>> combos =
			(ArrayList<HashMap<String, Object>>) row.get("combos");

		ArrayList<String> comboLabels = new ArrayList<String>();

		for (HashMap<String, Object> combo : combos) {

			String label = combo.get("label") == null ? "" : combo.get("label").toString();
			double balance = combo.get("balance") == null ? 0 : (Double) combo.get("balance");

			comboLabels.add(label + " - " + AmountFormat.formatPlain(balance) + " in stock");
		}

		ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(
			activity, android.R.layout.simple_spinner_item, comboLabels
		);

		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(spinnerAdapter);

		return convertView;
	}
}
