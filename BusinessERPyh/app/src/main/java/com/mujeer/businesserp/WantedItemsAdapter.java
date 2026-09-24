package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class WantedItemsAdapter extends BaseAdapter {

    public interface OnFulfilledToggleListener {
        void onToggle(int wantedItemId, boolean fulfilled);
    }

    private final Activity activity;
    private final ArrayList<HashMap<String, Object>> list;
    private final OnFulfilledToggleListener listener;

    public WantedItemsAdapter(
        Activity activity,
        ArrayList<HashMap<String, Object>> list,
        OnFulfilledToggleListener listener) {

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
            convertView = LayoutInflater.from(activity)
                .inflate(R.layout.wanted_item_row, parent, false);
        }

        final HashMap<String, Object> row = list.get(position);

        TextView tvName = convertView.findViewById(R.id.tv_wanted_item_name);
        TextView tvMeta = convertView.findViewById(R.id.tv_wanted_item_meta);
        TextView tvNotes = convertView.findViewById(R.id.tv_wanted_item_notes);
        CheckBox cbFulfilled = convertView.findViewById(R.id.cb_wanted_item_fulfilled);

        tvName.setText(String.valueOf(row.get("item_name")));

        String meta = row.get("date") + " " + row.get("time");

        if (row.get("party_name") != null) {
            meta += " · " + row.get("party_name");
        }

        tvMeta.setText(meta);

        Object notes = row.get("notes");

        if (notes != null && notes.toString().trim().length() > 0) {
            tvNotes.setText(notes.toString());
            tvNotes.setVisibility(View.VISIBLE);
        } else {
            tvNotes.setVisibility(View.GONE);
        }

        // Avoid firing the listener while we're just recycling the view
        // and setting the checkbox to match the underlying data.
        cbFulfilled.setOnCheckedChangeListener(null);
        cbFulfilled.setChecked(Boolean.TRUE.equals(row.get("fulfilled")));

        cbFulfilled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (listener != null) {
                    listener.onToggle((Integer) row.get("id"), isChecked);
                }
            }
        });

        return convertView;
    }
}
