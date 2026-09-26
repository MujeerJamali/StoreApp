package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// Backs the "Split Across Purchase(s)" picker in PurchaseCostEditActivity -
// each row is one existing purchase, checkable to include it in the split,
// with a per-row "to party balance" toggle (see TABLE_PURCHASE_COST_LINKS)
// that only appears once that row is checked. Selection/to-party state is
// keyed by purchase id (not row position) so it survives the list being
// re-filtered by the search box.
public class PurchaseSelectionAdapter extends BaseAdapter implements Filterable {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> originalList;
	private final ArrayList<HashMap<String, Object>> filteredList;

	private final Set<Integer> selectedIds = new HashSet<>();
	private final Set<Integer> toPartyIds = new HashSet<>();

	// The purchase cost's own total amount, kept in sync by the activity
	// as the user types it, so each row's live "≈ share, ≈ amount"
	// preview updates as they go.
	private double totalAmount = 0;

	public PurchaseSelectionAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.originalList = list;
		this.filteredList = new ArrayList<>(list);
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
		notifyDataSetChanged();
	}

	// Preselects one purchase (e.g. opened via "+ Add Purchase Cost" from
	// that purchase's own edit screen) - see PurchaseCostEditActivity.
	public void preselect(int purchaseId) {
		selectedIds.add(purchaseId);
		notifyDataSetChanged();
	}

	public ArrayList<HashMap<String, Object>> getSelections() {

		ArrayList<HashMap<String, Object>> selections = new ArrayList<>();

		for (HashMap<String, Object> purchase : originalList) {

			int purchaseId = (Integer) purchase.get("id");

			if (selectedIds.contains(purchaseId)) {

				HashMap<String, Object> selection = new HashMap<>();
				selection.put("purchase_id", purchaseId);
				selection.put("to_party", toPartyIds.contains(purchaseId));

				selections.add(selection);
			}
		}

		return selections;
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

		convertView = LayoutInflater.from(activity)
			.inflate(R.layout.purchase_cost_link_row, parent, false);

		HashMap<String, Object> purchase = filteredList.get(position);
		final int purchaseId = (Integer) purchase.get("id");
		final double purchaseTotal = (Double) purchase.get("grand_total");

		CheckBox cbSelect = convertView.findViewById(R.id.cb_select_purchase);
		final View containerOptions = convertView.findViewById(R.id.container_link_options);
		final CheckBox cbToParty = convertView.findViewById(R.id.cb_to_party);
		final TextView tvPreview = convertView.findViewById(R.id.tv_link_share_preview);

		cbSelect.setText(
			purchase.get("party_name") + " - " +
			(purchase.get("invoice_no") == null ? "no invoice" : purchase.get("invoice_no")) +
			" - " + purchase.get("date") + " - " + AmountFormat.format(purchaseTotal)
		);

		boolean isSelected = selectedIds.contains(purchaseId);
		boolean isToParty = toPartyIds.contains(purchaseId);

		cbSelect.setOnCheckedChangeListener(null);
		cbSelect.setChecked(isSelected);

		cbToParty.setOnCheckedChangeListener(null);
		cbToParty.setChecked(isToParty);

		containerOptions.setVisibility(isSelected ? View.VISIBLE : View.GONE);
		updatePreview(tvPreview, purchaseTotal);

		cbSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean checked) {

				if (checked) {
					selectedIds.add(purchaseId);
				} else {
					selectedIds.remove(purchaseId);
					toPartyIds.remove(purchaseId);
				}

				notifyDataSetChanged();
			}
		});

		cbToParty.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean checked) {

				if (checked) {
					toPartyIds.add(purchaseId);
				} else {
					toPartyIds.remove(purchaseId);
				}
			}
		});

		return convertView;
	}

	// Rough live preview only - proportional-by-value share among
	// currently selected purchases. The real split (with rounding and
	// the last selection absorbing the remainder) happens at save time
	// in DatabaseHelper#applyPurchaseCostLinks().
	private void updatePreview(TextView tvPreview, double purchaseTotal) {

		double selectedTotalsSum = 0;

		for (HashMap<String, Object> purchase : originalList) {

			int id = (Integer) purchase.get("id");

			if (selectedIds.contains(id)) {
				selectedTotalsSum += (Double) purchase.get("grand_total");
			}
		}

		if (selectedTotalsSum <= 0 || totalAmount <= 0) {
			tvPreview.setText("");
			return;
		}

		double sharePercent = (purchaseTotal / selectedTotalsSum) * 100;
		double shareAmount = totalAmount * (purchaseTotal / selectedTotalsSum);

		tvPreview.setText(
			"≈ " + AmountFormat.formatPlain(sharePercent) + "% · ≈ " +
			AmountFormat.format(shareAmount)
		);
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

						String partyName = (String) map.get("party_name");
						String invoiceNo = (String) map.get("invoice_no");

						if (SearchUtils.matchesTokensAcrossFields(
							constraint.toString(),
							partyName + " " + (invoiceNo == null ? "" : invoiceNo))) {

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
