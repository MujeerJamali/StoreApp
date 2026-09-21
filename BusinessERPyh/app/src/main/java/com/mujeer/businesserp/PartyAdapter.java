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

public class PartyAdapter extends BaseAdapter implements Filterable {

    Activity activity;

    ArrayList<HashMap<String, Object>> originalList;
    ArrayList<HashMap<String, Object>> filteredList;

    public PartyAdapter(Activity activity,
                        ArrayList<HashMap<String, Object>> list) {

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
				.inflate(R.layout.party_view, parent, false);
        }

        TextView tv = convertView.findViewById(R.id.tv_party_name);
        TextView tvBalance = convertView.findViewById(R.id.tv_party_balance);

        HashMap<String, Object> party = filteredList.get(position);

        tv.setText((String) party.get("name"));

        double balance = 0;

        if (party.get("balance") != null) {
            balance = (Double) party.get("balance");
        }

        if (balance > 0) {

            tvBalance.setText(String.format("+%.2f", balance));
            tvBalance.setTextColor(activity.getResources().getColor(R.color.success));

        } else if (balance < 0) {

            tvBalance.setText(String.format("-%.2f", Math.abs(balance)));
            tvBalance.setTextColor(activity.getResources().getColor(R.color.danger));

        } else {

            tvBalance.setText("0.00");
            tvBalance.setTextColor(activity.getResources().getColor(R.color.text_secondary));
        }

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
            protected void publishResults(CharSequence constraint,
                                          FilterResults results) {

                filteredList.clear();
                filteredList.addAll((ArrayList<HashMap<String, Object>>) results.values);

                notifyDataSetChanged();
            }
        };
    }
}
