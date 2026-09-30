package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class SampleShoesAdapter extends BaseAdapter {

	public interface OnRemoveListener {
		void onRemove(int id);
	}

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;
	private final OnRemoveListener listener;

	public SampleShoesAdapter(
		Activity activity, ArrayList<HashMap<String, Object>> list, OnRemoveListener listener) {

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

		if (convertView == null) {

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.sample_shoe_row, parent, false
			);
		}

		TextView tv_code = convertView.findViewById(R.id.tv_sample_code_badge);
		TextView tv_name = convertView.findViewById(R.id.tv_sample_name);
		TextView tv_size = convertView.findViewById(R.id.tv_sample_size);
		Button btn_remove = convertView.findViewById(R.id.btn_sample_remove);

		final HashMap<String, Object> row = list.get(position);

		tv_code.setText(row.get("code") == null ? "" : row.get("code").toString());
		tv_name.setText(row.get("name") == null ? "" : row.get("name").toString());
		tv_size.setText(row.get("combo_label") == null ? "" : row.get("combo_label").toString());

		btn_remove.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					if (listener != null) {
						listener.onRemove((Integer) row.get("id"));
					}
				}
			});

		return convertView;
	}
}
