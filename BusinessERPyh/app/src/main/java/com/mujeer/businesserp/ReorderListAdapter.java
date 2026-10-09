package com.mujeer.businesserp;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for ReorderListActivity. Each row is one reorder
// suggestion (see DatabaseHelper.getReorderSuggestions()) with a
// checkbox (checked by default - unchecking excludes it from "Convert
// Checked to Draft Purchase(s)" without otherwise dismissing it) and an
// editable order-quantity field, since the whole point of this screen is
// that nothing here is a fixed number - the user can freely override
// what the formula suggested before committing to it.
public class ReorderListAdapter extends BaseAdapter {

	public interface Listener {
		void onIgnore(int position);
	}

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;
	private final Listener listener;

	public ReorderListAdapter(
		Activity activity, ArrayList<HashMap<String, Object>> list, Listener listener) {

		this.activity = activity;
		this.list = list;
		this.listener = listener;
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

		// Always inflates a fresh row rather than reusing convertView -
		// this list is small (one suggestion per low-stock item/combo in
		// a single shop) and each row owns a live EditText/CheckBox whose
		// state belongs to that specific suggestion, not to a recycled
		// view's previous occupant.
		View view = LayoutInflater.from(activity).inflate(
			R.layout.reorder_suggestion_row, parent, false
		);

		final HashMap<String, Object> row = list.get(position);

		TextView tv_name = view.findViewById(R.id.tv_reorder_name);
		TextView tv_code_supplier = view.findViewById(R.id.tv_reorder_code_supplier);
		TextView tv_stock = view.findViewById(R.id.tv_reorder_stock);
		TextView tv_cost = view.findViewById(R.id.tv_reorder_cost);
		TextView tv_ignore = view.findViewById(R.id.tv_reorder_ignore);
		final CheckBox cb_include = view.findViewById(R.id.cb_include);
		final EditText et_qty = view.findViewById(R.id.et_reorder_qty);

		String name = row.get("name") == null ? "" : row.get("name").toString();
		String comboLabel = row.get("combo_label") == null ? "" : row.get("combo_label").toString();

		tv_name.setText(comboLabel.isEmpty() ? name : name + " - " + comboLabel);

		String code = row.get("code") == null ? "" : row.get("code").toString();
		String supplierName =
			row.get("supplier_party_name") == null ? "No Supplier" : row.get("supplier_party_name").toString();

		tv_code_supplier.setText("Code " + code + " - " + supplierName);

		double currentStock = row.get("current_stock") == null ? 0 : (Double) row.get("current_stock");
		double velocity = row.get("velocity_per_day") == null ? 0 : (Double) row.get("velocity_per_day");

		tv_stock.setText(
			"Current stock: " + AmountFormat.formatPlain(currentStock) +
			"  (selling ~" + AmountFormat.format(velocity) + "/day)"
		);

		double suggestedQty = row.get("suggested_qty") == null ? 0 : (Double) row.get("suggested_qty");

		et_qty.setText(AmountFormat.formatPlain(suggestedQty));

		double purchasePriceEach = suggestedQty > 0 ?
			((Double) row.get("estimated_cost")) / suggestedQty : 0;

		tv_cost.setText("Est. cost " + AmountFormat.format((Double) row.get("estimated_cost")));

		cb_include.setChecked(true);
		row.put("included", true);

		cb_include.setOnCheckedChangeListener(null);
		cb_include.setOnCheckedChangeListener(
			new android.widget.CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(
					android.widget.CompoundButton buttonView, boolean isChecked) {
					row.put("included", isChecked);
				}
			}
		);

		row.put("edited_qty", suggestedQty);

		final double finalPurchasePriceEach = purchasePriceEach;

		et_qty.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {

					double qty = 0;

					try {
						qty = Double.parseDouble(s.toString());
					} catch (Exception e) {
					}

					row.put("edited_qty", qty);
					row.put("edited_cost", qty * finalPurchasePriceEach);
				}
			}
		);

		tv_ignore.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					if (listener != null) {
						listener.onIgnore(position);
					}
				}
			}
		);

		return view;
	}
}
