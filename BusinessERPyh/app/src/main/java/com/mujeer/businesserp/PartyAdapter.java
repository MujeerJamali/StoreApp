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
        TextView tvMeta = convertView.findViewById(R.id.tv_party_meta);
        TextView tvBalance = convertView.findViewById(R.id.tv_party_balance);

        HashMap<String, Object> party = filteredList.get(position);

        tv.setText((String) party.get("name"));

        // Row #96's own "simplify to single-line" request - the old
        // two-line layout (name, then a full sentence of meta below it)
        // becomes one short tag alongside the name instead of its own
        // row, so the screen reads as a plain list again rather than a
        // stack of small cards. Only present at all for a tagged
        // (non-Regular) customer or a list that actually carries
        // days_since (DatabaseHelper.getPartiesWithActivity()'s sorted
        // view) - the plain getParties() list has neither, so the tag
        // stays hidden for that case exactly as before.
        String customerType = (String) party.get("customer_type");

        String customerTypeTag =
            DatabaseHelper.CUSTOMER_TYPE_WHOLESALE.equals(customerType) ? "Wholesale" :
            DatabaseHelper.CUSTOMER_TYPE_ONE_TIME.equals(customerType) ? "One-Time" :
            null;

        if (customerTypeTag != null) {

            tvMeta.setText(customerTypeTag);
            tvMeta.setVisibility(View.VISIBLE);

        } else if (party.containsKey("days_since")) {

            int daysSince = party.get("days_since") == null ? -1 : (Integer) party.get("days_since");

            tvMeta.setText(daysSince < 0 ? "No activity" : daysSince + "d ago");
            tvMeta.setVisibility(View.VISIBLE);

        } else {

            tvMeta.setVisibility(View.GONE);
        }

        double balance = 0;

        if (party.get("balance") != null) {
            balance = (Double) party.get("balance");
        }

        if (balance > 0) {

            tvBalance.setText("+" + AmountFormat.format(balance));
            tvBalance.setTextColor(activity.getResources().getColor(R.color.success));

        } else if (balance < 0) {

            tvBalance.setText("-" + AmountFormat.format(Math.abs(balance)));
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
