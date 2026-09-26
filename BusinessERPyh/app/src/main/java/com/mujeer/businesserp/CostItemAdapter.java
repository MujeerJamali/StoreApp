package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class CostItemAdapter extends BaseAdapter implements Filterable {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> originalList;
	private final ArrayList<HashMap<String, Object>> filteredList;

	public CostItemAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.originalList = list;
		this.filteredList = new ArrayList<>(list);
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
				.inflate(R.layout.cost_item_row, parent, false);
		}

		HashMap<String, Object> row = filteredList.get(position);

		TextView tvName = convertView.findViewById(R.id.tv_cost_item_name);
		tvName.setText(String.valueOf(row.get("name")));

		return convertView;
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

						String name = (String) map.get("name");

						if (SearchUtils.matchesTokensAcrossFields(
							constraint.toString(), name)) {

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
			protected void publishResults(CharSequence constraint, FilterResults results) {

				filteredList.clear();
				filteredList.addAll((ArrayList<HashMap<String, Object>>) results.values);

				notifyDataSetChanged();
			}
		};
	}
}
