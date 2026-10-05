package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// Shared list adapter for GlobalSearchActivity's three result
// sections (Parties/Items/Invoices) - each row's own HashMap already
// carries plain "primary"/"secondary" display strings, prepared by the
// Activity from whichever source list it came from, so one generic
// adapter renders all three.
public class GlobalSearchAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public GlobalSearchAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.global_search_row, parent, false
			);
		}

		TextView tv_primary = convertView.findViewById(R.id.tv_global_search_primary);
		TextView tv_secondary = convertView.findViewById(R.id.tv_global_search_secondary);

		HashMap<String, Object> row = list.get(position);

		tv_primary.setText((String) row.get("primary"));
		tv_secondary.setText((String) row.get("secondary"));

		return convertView;
	}
}
