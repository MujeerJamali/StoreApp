package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for RecentlyDeletedActivity - each row is one deleted
// OR edited Purchase/Sale still in the trash. Tap restores/undoes it,
// long-press permanently deletes it (see the Activity's own
// listeners).
public class RecentlyDeletedAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public RecentlyDeletedAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.recently_deleted_row, parent, false
			);
		}

		TextView tv_label = convertView.findViewById(R.id.tv_recently_deleted_label);
		TextView tv_meta = convertView.findViewById(R.id.tv_recently_deleted_meta);

		HashMap<String, Object> row = list.get(position);

		tv_label.setText((String) row.get("label"));

		String type = (String) row.get("type");

		boolean isPurchase = "purchase".equals(type) || "purchase_edit".equals(type);
		boolean isEdit = "sale_edit".equals(type) || "purchase_edit".equals(type);

		tv_meta.setText(
			(isPurchase ? "Purchase" : "Sale") +
			(isEdit ? " · Edited " : " · Deleted ") +
			RelativeDate.format((String) row.get("deleted_date"))
		);

		return convertView;
	}
}
