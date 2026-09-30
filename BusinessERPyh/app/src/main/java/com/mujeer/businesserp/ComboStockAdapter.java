package com.mujeer.businesserp;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for ComboStockReportActivity's single item list - each
// row is one shoe item with its name, plus every size it comes in and
// its stock, with the size matching the current filter selection (if
// any) moved first and shown in bold.
public class ComboStockAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	// The variety value label (e.g. "9") currently selected in the
	// filter dropdown, or null when "All Sizes" is selected - either
	// way every size still shows, this only controls ordering/bolding.
	private String selectedLabel = null;

	public ComboStockAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.list = list;
	}

	public void setSelectedFilter(String selectedLabel) {
		this.selectedLabel = selectedLabel;
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
				R.layout.combo_stock_row, parent, false
			);
		}

		TextView tv_name = convertView.findViewById(R.id.tv_combo_item_name);
		TextView tv_sizes = convertView.findViewById(R.id.tv_combo_item_sizes);

		HashMap<String, Object> row = list.get(position);

		tv_name.setText(row.get("name") == null ? "" : row.get("name").toString());

		ArrayList<HashMap<String, Object>> combos =
			(ArrayList<HashMap<String, Object>>) row.get("combos");

		tv_sizes.setText(buildSizesText(combos));

		return convertView;
	}

	private CharSequence buildSizesText(ArrayList<HashMap<String, Object>> combos) {

		if (combos == null || combos.isEmpty()) {
			return "No sizes";
		}

		// The size matching the current filter (if any) goes first; the
		// rest keep their existing order.
		HashMap<String, Object> matched = null;
		ArrayList<HashMap<String, Object>> ordered = new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> combo : combos) {

			String label = combo.get("label") == null ? "" : combo.get("label").toString();

			if (matched == null && selectedLabel != null && label.equals(selectedLabel)) {
				matched = combo;
			} else {
				ordered.add(combo);
			}
		}

		if (matched != null) {
			ordered.add(0, matched);
		}

		SpannableStringBuilder text = new SpannableStringBuilder();

		for (int i = 0; i < ordered.size(); i++) {

			HashMap<String, Object> combo = ordered.get(i);

			String label = combo.get("label") == null ? "" : combo.get("label").toString();
			double balance = combo.get("balance") == null ? 0 : (Double) combo.get("balance");

			String entry = label + " (" + AmountFormat.formatPlain(balance) + ")";

			int start = text.length();
			text.append(entry);

			if (combo == matched) {

				text.setSpan(
					new StyleSpan(Typeface.BOLD), start, text.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				);
			}

			if (i < ordered.size() - 1) {
				text.append("   ·   ");
			}
		}

		return text;
	}
}
