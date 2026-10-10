package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Dedicated view/edit/delete/add screen for reorder_suggestion_log
// (ReorderSettingsActivity's "View / Edit History" button opens this) -
// the blind "Export History" .csv button there only ever let the user
// look at this log outside the app; this lets them correct a wrong
// outcome, delete a bad row, or backfill a missed one without leaving
// BusinessERP. Paginated 30 rows at a time (same "Load More" convention
// as Itemviewactivity's own transaction history) since the log grows
// with every reorder suggestion ever shown, unbounded, for as long as
// the shop uses the app.
// =====================
public class ReorderLearningHistoryActivity extends Activity {

	private static final int PAGE_SIZE = 30;

	private static final String[] OUTCOME_VALUES = {
		null, "accepted", "ignored", "overstocked", "ran_out_before_restock"
	};

	private static final String[] OUTCOME_LABELS = {
		"Pending", "Accepted", "Ignored", "Overstocked", "Ran Out Before Restock"
	};

	private TextView tv_reorder_history_empty;
	private LinearLayout container_reorder_history;
	private Button btn_load_more_reorder_history;
	private Button btn_add_reorder_history_entry;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> historyList =
		new ArrayList<HashMap<String, Object>>();

	private int totalCount = 0;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reorder_learning_history_activity);

		setTitle("Reorder Learning History");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Reorder Learning History",
			"Every past Reorder List suggestion and what happened to it - the raw log the learning loop reads back through to adjust future suggestions (see DatabaseHelper.getLearningAdjustmentMultiplier()). Tap a row to correct its outcome or delete it, or add one manually to backfill a decision this log missed. Adding is item-level only - a row already tied to a specific variety combo keeps that combo, but a new row can't pick one."
		);

		tv_reorder_history_empty = findViewById(R.id.tv_reorder_history_empty);
		container_reorder_history = findViewById(R.id.container_reorder_history);
		btn_load_more_reorder_history = findViewById(R.id.btn_load_more_reorder_history);
		btn_add_reorder_history_entry = findViewById(R.id.btn_add_reorder_history_entry);

		db = new DatabaseHelper(this);

		btn_load_more_reorder_history.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadMore();
				}
			}
		);

		btn_add_reorder_history_entry.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptAddOrEdit(null);
				}
			}
		);

		loadFirstPage();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadFirstPage();
	}

	private void loadFirstPage() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final int count = db.getReorderLearningHistoryCount();
					final ArrayList<HashMap<String, Object>> page =
						db.getReorderLearningHistoryPage(PAGE_SIZE, 0);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								totalCount = count;
								historyList.clear();
								historyList.addAll(page);

								renderList();
							}
						}
					);
				}
			}
		).start();
	}

	private void loadMore() {

		final long myGeneration = loadGeneration;
		final int offset = historyList.size();

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> page =
						db.getReorderLearningHistoryPage(PAGE_SIZE, offset);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								historyList.addAll(page);
								renderList();
							}
						}
					);
				}
			}
		).start();
	}

	private void renderList() {

		container_reorder_history.removeAllViews();

		if (historyList.isEmpty()) {

			tv_reorder_history_empty.setVisibility(View.VISIBLE);
			btn_load_more_reorder_history.setVisibility(View.GONE);
			return;
		}

		tv_reorder_history_empty.setVisibility(View.GONE);

		LayoutInflater inflater = LayoutInflater.from(this);

		for (final HashMap<String, Object> row : historyList) {

			View view = inflater.inflate(
				R.layout.discount_stop_restock_row, container_reorder_history, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			String itemName = String.valueOf(row.get("item_name"));
			String comboLabel = String.valueOf(row.get("combo_label"));

			tv_name.setText(
				comboLabel.length() > 0 ? itemName + " (" + comboLabel + ")" : itemName
			);

			double suggestedQty = (Double) row.get("suggested_qty");

			tv_detail.setText(
				row.get("suggested_date") + " - Qty " + AmountFormat.formatPlain(suggestedQty)
			);

			String outcome = (String) row.get("outcome");

			tv_badge.setText(outcomeLabel(outcome));
			tv_badge.setTextColor(getResources().getColor(outcomeColor(outcome)));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						promptAddOrEdit(row);
					}
				}
			);

			container_reorder_history.addView(view);
		}

		btn_load_more_reorder_history.setVisibility(
			historyList.size() < totalCount ? View.VISIBLE : View.GONE
		);
	}

	private String outcomeLabel(String outcome) {

		for (int i = 0; i < OUTCOME_VALUES.length; i++) {

			if (outcome == null ? OUTCOME_VALUES[i] == null : outcome.equals(OUTCOME_VALUES[i])) {
				return OUTCOME_LABELS[i];
			}
		}

		return outcome;
	}

	private int outcomeColor(String outcome) {

		if ("overstocked".equals(outcome) || "ran_out_before_restock".equals(outcome)) {
			return R.color.danger;
		}

		if ("accepted".equals(outcome) || "ignored".equals(outcome)) {
			return R.color.success;
		}

		return R.color.text_secondary;
	}

	// row is null for "+ Add Entry", or an existing historyList row to
	// edit/delete. The item field resolves by exact name match against
	// a freshly loaded item list, same convention as Paymenteditactivity's
	// own party field - fine here since this is a deliberately simple
	// correction/backfill screen, not a full picker with "+Add New."
	private void promptAddOrEdit(final HashMap<String, Object> row) {

		final boolean isEdit = row != null;

		View view = getLayoutInflater().inflate(R.layout.dialog_reorder_learning_entry, null);

		final AutoCompleteTextView et_item = view.findViewById(R.id.et_entry_item);
		final TextView tv_combo_label = view.findViewById(R.id.tv_entry_combo_label);
		final EditText et_suggested_date = view.findViewById(R.id.et_entry_suggested_date);
		final EditText et_suggested_qty = view.findViewById(R.id.et_entry_suggested_qty);
		final Spinner spinner_outcome = view.findViewById(R.id.spinner_entry_outcome);
		final EditText et_outcome_date = view.findViewById(R.id.et_entry_outcome_date);

		final ArrayList<HashMap<String, Object>> items = db.getItems();
		final ArrayList<String> itemNames = new ArrayList<String>();

		for (HashMap<String, Object> item : items) {
			itemNames.add(String.valueOf(item.get("name")));
		}

		ArrayAdapter<String> itemAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_dropdown_item_1line, itemNames
		);

		et_item.setAdapter(itemAdapter);
		et_item.setThreshold(1);

		ArrayAdapter<String> outcomeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, OUTCOME_LABELS
		);

		outcomeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_outcome.setAdapter(outcomeAdapter);

		if (isEdit) {

			et_item.setText(String.valueOf(row.get("item_name")));

			String comboLabel = String.valueOf(row.get("combo_label"));

			if (comboLabel.length() > 0) {

				tv_combo_label.setText("Variety: " + comboLabel + " (can't be changed here)");
				tv_combo_label.setVisibility(View.VISIBLE);
			}

			et_suggested_date.setText(String.valueOf(row.get("suggested_date")));
			et_suggested_qty.setText(AmountFormat.formatPlain((Double) row.get("suggested_qty")));

			String outcome = (String) row.get("outcome");

			for (int i = 0; i < OUTCOME_VALUES.length; i++) {

				if (outcome == null ? OUTCOME_VALUES[i] == null : outcome.equals(OUTCOME_VALUES[i])) {
					spinner_outcome.setSelection(i);
					break;
				}
			}

			et_outcome_date.setText(String.valueOf(row.get("outcome_date")));

		} else {

			et_suggested_date.setText(dateFormat.format(new java.util.Date()));
		}

		et_suggested_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_suggested_date);
				}
			}
		);

		et_outcome_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_outcome_date);
				}
			}
		);

		AlertDialog.Builder builder = new AlertDialog.Builder(this)
			.setTitle(isEdit ? "Edit Entry" : "Add Entry")
			.setView(view)
			.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						save(row, items, et_item, et_suggested_date, et_suggested_qty,
							spinner_outcome, et_outcome_date);
					}
				}
			)
			.setNegativeButton("Cancel", null);

		if (isEdit) {

			final int id = (Integer) row.get("id");

			builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						confirmDelete(id);
					}
				}
			);
		}

		builder.show();
	}

	private void showDatePicker(final EditText target) {

		Calendar calendar = Calendar.getInstance();

		try {

			String existing = target.getText().toString();

			if (existing.length() > 0) {
				calendar.setTime(dateFormat.parse(existing));
			}

		} catch (Exception e) {
		}

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
					@Override
					public void onDateSet(
						android.widget.DatePicker view, int year, int month, int dayOfMonth) {

						target.setText(
							String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
						);
					}
				},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void save(
		HashMap<String, Object> row, ArrayList<HashMap<String, Object>> items,
		AutoCompleteTextView et_item, EditText et_suggested_date, EditText et_suggested_qty,
		Spinner spinner_outcome, EditText et_outcome_date) {

		String itemName = et_item.getText().toString().trim();

		Integer itemId = null;

		for (HashMap<String, Object> item : items) {

			if (String.valueOf(item.get("name")).equals(itemName)) {
				itemId = (Integer) item.get("id");
				break;
			}
		}

		if (itemId == null) {

			Toast.makeText(this, "Pick an item from the list", Toast.LENGTH_SHORT).show();
			return;
		}

		String suggestedDate = et_suggested_date.getText().toString().trim();

		if (suggestedDate.isEmpty()) {

			Toast.makeText(this, "Set a suggested date", Toast.LENGTH_SHORT).show();
			return;
		}

		double suggestedQty;

		try {

			suggestedQty = Double.parseDouble(et_suggested_qty.getText().toString().trim());

		} catch (Exception e) {

			Toast.makeText(this, "Enter a valid suggested quantity", Toast.LENGTH_SHORT).show();
			return;
		}

		String outcome = OUTCOME_VALUES[spinner_outcome.getSelectedItemPosition()];
		String outcomeDate = et_outcome_date.getText().toString().trim();

		if (outcomeDate.isEmpty()) {
			outcomeDate = null;
		}

		if (row == null) {

			db.insertReorderLearningEntry(itemId, suggestedDate, suggestedQty, outcome, outcomeDate);
			Toast.makeText(this, "Entry added", Toast.LENGTH_SHORT).show();

		} else {

			int id = (Integer) row.get("id");

			db.updateReorderLearningEntry(
				id, itemId, suggestedDate, suggestedQty, outcome, outcomeDate
			);

			Toast.makeText(this, "Entry updated", Toast.LENGTH_SHORT).show();
		}

		loadFirstPage();
	}

	private void confirmDelete(final int id) {

		new AlertDialog.Builder(this)
			.setTitle("Delete Entry")
			.setMessage(
				"Delete this reorder history entry? This cannot be undone, and it will " +
				"no longer influence future reorder suggestions for this item."
			)
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						db.deleteReorderLearningEntry(id);
						Toast.makeText(
							ReorderLearningHistoryActivity.this, "Entry deleted", Toast.LENGTH_SHORT
						).show();

						loadFirstPage();
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}
}
