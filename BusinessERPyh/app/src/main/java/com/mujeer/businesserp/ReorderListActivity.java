package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

// =====================
// What to reorder right now, computed by DatabaseHelper.
// getReorderSuggestions() from recent sales speed vs current stock (see
// its own comment, and ReorderSettings for every input the formula
// uses). Nothing here is final until the user acts: a suggestion can be
// unchecked, its quantity edited, or individually "Ignore"d; checked
// rows get batched by supplier into one draft Purchase each via
// "Convert Checked to Draft Purchase(s)", which still needs to be opened
// from Drafts and actually saved like any other draft before it affects
// stock/cash. Every decision (converted or ignored) is logged via
// DatabaseHelper.recordReorderDecision() for the learning loop to read
// back later.
// =====================
public class ReorderListActivity extends Activity implements ReorderListAdapter.Listener {

	private TextView tv_empty;
	private TextView tv_cash_note;
	private ListView lv_reorder;
	private ImageButton btn_reorder_settings;
	private android.widget.Button btn_convert_to_draft;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> suggestionList =
		new ArrayList<HashMap<String, Object>>();

	private ReorderListAdapter adapter;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reorder_list_activity);

		setTitle("Reorder List");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Reorder List",
			"Items worth restocking, based on recent sales speed vs current stock - see the gear icon to tune how this is calculated. Nothing here is final: uncheck, edit the quantity, or Ignore anything, then convert the rest to a draft Purchase per supplier."
		);

		tv_empty = findViewById(R.id.tv_empty);
		tv_cash_note = findViewById(R.id.tv_cash_note);
		lv_reorder = findViewById(R.id.lv_reorder);
		btn_reorder_settings = findViewById(R.id.btn_reorder_settings);
		btn_convert_to_draft = findViewById(R.id.btn_convert_to_draft);

		db = new DatabaseHelper(this);

		adapter = new ReorderListAdapter(this, suggestionList, this);
		lv_reorder.setAdapter(adapter);

		btn_reorder_settings.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(ReorderListActivity.this, ReorderSettingsActivity.class));
				}
			}
		);

		btn_convert_to_draft.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					convertCheckedToDrafts();
				}
			}
		);

		loadSuggestions();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadSuggestions();
	}

	private void loadSuggestions() {

		final long myGeneration = ++loadGeneration;
		final android.content.Context appContext = getApplicationContext();

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getReorderSuggestions(appContext);

					final double cashBalance = db.getCashBalance();
					final double cashLimit =
						ReorderSettings.getEffectiveCashLimit(appContext, cashBalance);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								suggestionList.clear();
								suggestionList.addAll(result);

								adapter.notifyDataSetChanged();

								updateEmptyState();
								updateCashNote(cashLimit);
							}
						}
					);
				}
			}
		).start();
	}

	private void updateEmptyState() {

		if (suggestionList.isEmpty()) {

			tv_empty.setVisibility(View.VISIBLE);
			lv_reorder.setVisibility(View.GONE);
			btn_convert_to_draft.setVisibility(View.GONE);

		} else {

			tv_empty.setVisibility(View.GONE);
			lv_reorder.setVisibility(View.VISIBLE);
			btn_convert_to_draft.setVisibility(View.VISIBLE);
		}
	}

	private void updateCashNote(double cashLimit) {

		if (cashLimit >= Double.MAX_VALUE) {

			tv_cash_note.setText("Cash-awareness is off (see Reorder Settings).");

		} else {

			tv_cash_note.setText("Cash available for restocking: " + AmountFormat.format(cashLimit));
		}
	}

	@Override
	public void onIgnore(int position) {

		if (position < 0 || position >= suggestionList.size()) {
			return;
		}

		HashMap<String, Object> row = suggestionList.get(position);

		db.recordReorderDecision(
			(Integer) row.get("item_id"),
			(Integer) row.get("combo_id"),
			(Double) row.get("suggested_qty"),
			"ignored"
		);

		suggestionList.remove(position);
		adapter.notifyDataSetChanged();
		updateEmptyState();

		Toast.makeText(this, "Ignored", Toast.LENGTH_SHORT).show();
	}

	// Groups every checked row by its inferred supplier (see
	// DatabaseHelper's getLastSupplier()) and creates one draft Purchase
	// per supplier group, pre-filled with each item's edited quantity and
	// its own purchase price. A group with no inferred supplier still
	// gets a draft - just with no party selected, same as any other
	// Purchase draft - so the user picks one when they finish it.
	private void convertCheckedToDrafts() {

		final ArrayList<HashMap<String, Object>> included = new ArrayList<HashMap<String, Object>>();

		double totalCost = 0;

		for (HashMap<String, Object> row : suggestionList) {

			Boolean isIncluded = (Boolean) row.get("included");

			if (isIncluded == null || !isIncluded) {
				continue;
			}

			Double editedQty = (Double) row.get("edited_qty");

			if (editedQty == null || editedQty <= 0) {
				continue;
			}

			included.add(row);

			Double editedCost = (Double) row.get("edited_cost");
			totalCost += editedCost == null ? 0 : editedCost;
		}

		if (included.isEmpty()) {
			Toast.makeText(this, "Nothing checked", Toast.LENGTH_SHORT).show();
			return;
		}

		double cashBalance = db.getCashBalance();
		double cashLimit = ReorderSettings.getEffectiveCashLimit(this, cashBalance);

		if (cashLimit < Double.MAX_VALUE && totalCost > cashLimit) {

			new AlertDialog.Builder(this)
				.setTitle("Exceeds Available Cash")
				.setMessage(
					"This order totals " + AmountFormat.format(totalCost) +
					", above the " + AmountFormat.format(cashLimit) +
					" available for restocking. Create the draft(s) anyway?"
				)
				.setPositiveButton("Create Anyway", new android.content.DialogInterface.OnClickListener() {
						@Override
						public void onClick(android.content.DialogInterface dialog, int which) {
							doConvertToDrafts(included);
						}
					}
				)
				.setNegativeButton("Cancel", null)
				.show();

			return;
		}

		doConvertToDrafts(included);
	}

	private void doConvertToDrafts(ArrayList<HashMap<String, Object>> included) {

		LinkedHashMap<String, ArrayList<HashMap<String, Object>>> bySupplier =
			new LinkedHashMap<String, ArrayList<HashMap<String, Object>>>();
		LinkedHashMap<String, Integer> supplierPartyIds = new LinkedHashMap<String, Integer>();

		for (HashMap<String, Object> row : included) {

			Integer partyId = (Integer) row.get("supplier_party_id");
			String supplierName =
				row.get("supplier_party_name") == null ? "No Supplier" : row.get("supplier_party_name").toString();

			String key = partyId == null ? "none" : String.valueOf(partyId);

			if (!bySupplier.containsKey(key)) {
				bySupplier.put(key, new ArrayList<HashMap<String, Object>>());
				supplierPartyIds.put(key, partyId);
			}

			bySupplier.get(key).add(row);
		}

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
		String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

		int draftCount = 0;

		for (String key : bySupplier.keySet()) {

			ArrayList<HashMap<String, Object>> groupRows = bySupplier.get(key);
			Integer partyId = supplierPartyIds.get(key);

			HashMap<String, Object> draftData = new HashMap<String, Object>();

			if (partyId != null) {
				draftData.put("party_id", partyId);
			}

			draftData.put("date", today);
			draftData.put("time", now);
			draftData.put("invoice_number", "");
			draftData.put("notes", "Created from Reorder List");
			draftData.put("amount_paid", "0");

			ArrayList<HashMap<String, Object>> draftItems = new ArrayList<HashMap<String, Object>>();

			String supplierLabel = "No Supplier";

			for (HashMap<String, Object> row : groupRows) {

				double qty = (Double) row.get("edited_qty");
				double purchasePriceEach = qty > 0 ? ((Double) row.get("edited_cost")) / qty : 0;

				HashMap<String, Object> itemMap = new HashMap<String, Object>();
				itemMap.put("item_id", row.get("item_id"));
				itemMap.put("code", row.get("code"));
				itemMap.put("name", row.get("name"));
				itemMap.put("quantity", qty);
				itemMap.put("purchase_price", purchasePriceEach);
				itemMap.put("total", qty * purchasePriceEach);
				itemMap.put("combo_id", row.get("combo_id"));

				draftItems.add(itemMap);

				supplierLabel =
					row.get("supplier_party_name") == null ? "No Supplier" : row.get("supplier_party_name").toString();

				db.recordReorderDecision(
					(Integer) row.get("item_id"),
					(Integer) row.get("combo_id"),
					qty,
					"accepted"
				);
			}

			draftData.put("items", draftItems);

			String encoded = DraftCodec.encode(draftData);

			if (encoded == null) {
				continue;
			}

			String label =
				"Purchase (Reorder) - " + supplierLabel + " - " + draftItems.size() +
				(draftItems.size() == 1 ? " item" : " items");

			db.insertDraft(DatabaseHelper.DRAFT_TYPE_PURCHASE, label, encoded, today, now);

			draftCount++;
		}

		suggestionList.removeAll(included);
		adapter.notifyDataSetChanged();
		updateEmptyState();

		Toast.makeText(
			this,
			"Created " + draftCount + (draftCount == 1 ? " draft purchase" : " draft purchases") +
			" - open Drafts to finish and save " + (draftCount == 1 ? "it" : "them"),
			Toast.LENGTH_LONG
		).show();
	}
}
