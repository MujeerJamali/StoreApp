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

public class DraftsAdapter extends BaseAdapter {

	public interface OnDeleteClickListener {
		void onDeleteClick(int draftId);
	}

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;
	private final OnDeleteClickListener deleteListener;

	public DraftsAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list,
		OnDeleteClickListener deleteListener) {

		this.activity = activity;
		this.list = list;
		this.deleteListener = deleteListener;
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
			convertView = LayoutInflater.from(activity)
				.inflate(R.layout.draft_row, parent, false);
		}

		final HashMap<String, Object> row = list.get(position);

		TextView tvLabel = convertView.findViewById(R.id.tv_draft_label);
		TextView tvDate = convertView.findViewById(R.id.tv_draft_date);
		Button btnDelete = convertView.findViewById(R.id.btn_draft_delete);

		tvLabel.setText(String.valueOf(row.get("label")));

		tvDate.setText(
			RelativeDate.format(String.valueOf(row.get("date"))) +
			" " + row.get("time")
		);

		btnDelete.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (deleteListener != null) {
						deleteListener.onDeleteClick((Integer) row.get("id"));
					}
				}
			});

		return convertView;
	}
}
