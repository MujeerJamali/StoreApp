package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Same list/filter shape as ItemAdapter, but with a checkbox per row driven
 * by an externally-owned selectedIds set (the caller toggles membership on
 * row click and calls notifyDataSetChanged() - this adapter only reflects
 * that state, it never mutates the set itself).
 */
public class SelectableItemAdapter extends BaseAdapter implements Filterable {

	Activity activity;

	ArrayList<HashMap<String, Object>> originalList;
	ArrayList<HashMap<String, Object>> filteredList;
	Set<Integer> selectedIds;

	public SelectableItemAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list,
		Set<Integer> selectedIds) {

		this.activity = activity;
		this.originalList = list;
		this.filteredList = new ArrayList<>(list);
		this.selectedIds = selectedIds;
	}

	@Override
	public int getCount() {
		return filteredList.size();
	}

	@Override
	public Object getItem(int position) {
		return filteredList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return (Integer) filteredList.get(position).get("id");
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {
			convertView = LayoutInflater.from(activity)
				.inflate(R.layout.row_item_selectable, parent, false);
		}

		CheckBox cb = convertView.findViewById(R.id.cb_item_selected);
		TextView tvCode = convertView.findViewById(R.id.tv_item_code);
		TextView tvName = convertView.findViewById(R.id.tv_item_name);
		TextView tvStock = convertView.findViewById(R.id.tv_item_stock);

		HashMap<String, Object> item = filteredList.get(position);

		tvCode.setText((String) item.get("code"));
		tvName.setText((String) item.get("name"));

		double stock = 0;

		if (item.get("balance") != null) {
			stock = (Double) item.get("balance");
		}

		tvStock.setText("Stock: " + formatQty(stock));

		if (stock <= 0) {
			tvStock.setTextColor(activity.getResources().getColor(R.color.danger));
		} else {
			tvStock.setTextColor(activity.getResources().getColor(R.color.mod_items));
		}

		int itemId = (Integer) item.get("id");

		cb.setChecked(selectedIds.contains(itemId));

		return convertView;
	}

	private String formatQty(double qty) {

		return AmountFormat.formatPlain(qty);
	}

	@Override
	public Filter getFilter() {

		return new Filter() {

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {

				ArrayList<HashMap<String, Object>> list = new ArrayList<>();

				if (constraint == null || constraint.length() == 0) {

					list.addAll(originalList);

				} else {

					for (HashMap<String, Object> map : originalList) {

						String code = (String) map.get("code");
						String name = (String) map.get("name");

						if (SearchUtils.matchesTokensAcrossFields(
							constraint.toString(), code, name)) {

							list.add(map);
						}
					}
				}

				FilterResults results = new FilterResults();
				results.values = list;
				results.count = list.size();

				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint,
										  FilterResults results) {

				filteredList.clear();
				filteredList.addAll((ArrayList<HashMap<String, Object>>) results.values);

				notifyDataSetChanged();
			}
		};
	}
}
