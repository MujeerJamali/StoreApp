package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// The user's own manually marked busy periods (approved feature list
// row #76) - a month plus a quantity boost, biasing
// DatabaseHelper.getReorderSuggestions() app-wide for that month on
// top of the per-item auto-detected seasonal pattern
// (getSeasonalMultiplier()). Opened from Reorder Settings.
// =====================
public class SeasonalCalendarActivity extends Activity {

	private TextView tv_no_seasonal_entries;
	private ListView lv_seasonal_entries;
	private Button btn_add_seasonal_entry;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> entryList;
	private SeasonalCalendarAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.seasonal_calendar_activity);

		setTitle("Seasonal Calendar");

		tv_no_seasonal_entries = findViewById(R.id.tv_no_seasonal_entries);
		lv_seasonal_entries = findViewById(R.id.lv_seasonal_entries);
		btn_add_seasonal_entry = findViewById(R.id.btn_add_seasonal_entry);

		lv_seasonal_entries.setEmptyView(tv_no_seasonal_entries);

		db = new DatabaseHelper(this);

		loadEntries();

		btn_add_seasonal_entry.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptAddOrEditEntry(null);
				}
			}
		);

		lv_seasonal_entries.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					@SuppressWarnings("unchecked")
					HashMap<String, Object> row =
						(HashMap<String, Object>) adapter.getItem(position);

					promptAddOrEditEntry(row);
				}
			}
		);

		lv_seasonal_entries.setOnItemLongClickListener(
			new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(
					AdapterView<?> parent, View view, int position, long id) {

					@SuppressWarnings("unchecked")
					HashMap<String, Object> row =
						(HashMap<String, Object>) adapter.getItem(position);

					confirmDeleteEntry((Integer) row.get("id"));
					return true;
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadEntries();
	}

	private void loadEntries() {

		entryList = db.getAllSeasonalCalendarEntries();
		adapter = new SeasonalCalendarAdapter(this, entryList);
		lv_seasonal_entries.setAdapter(adapter);
	}

	// Shared by "+ Add" and tapping an existing row - existingEntry null
	// means a brand-new one.
	private void promptAddOrEditEntry(final HashMap<String, Object> existingEntry) {

		int pad = (int) (16 * getResources().getDisplayMetrics().density);

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(pad, pad, pad, pad);

		TextView tv_label = new TextView(this);
		tv_label.setText("Label (e.g. \"Diwali season\")");
		container.addView(tv_label);

		final EditText et_label = new EditText(this);
		et_label.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		container.addView(et_label);

		TextView tv_month = new TextView(this);
		tv_month.setText("Month");
		tv_month.setPadding(0, pad, 0, 0);
		container.addView(tv_month);

		final Spinner spinner_month = new Spinner(this);

		ArrayList<String> monthNames = new ArrayList<String>();

		for (int m = 1; m <= 12; m++) {
			monthNames.add(SeasonalCalendarAdapter.monthName(m));
		}

		ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_dropdown_item, monthNames);

		spinner_month.setAdapter(monthAdapter);
		container.addView(spinner_month);

		TextView tv_percent = new TextView(this);
		tv_percent.setText("Quantity boost (%, e.g. 50 for +50%)");
		tv_percent.setPadding(0, pad, 0, 0);
		container.addView(tv_percent);

		final EditText et_percent = new EditText(this);
		et_percent.setInputType(
			InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		container.addView(et_percent);

		if (existingEntry != null) {

			et_label.setText((String) existingEntry.get("label"));
			spinner_month.setSelection(((Integer) existingEntry.get("month")) - 1);

			double multiplier = (Double) existingEntry.get("multiplier");
			et_percent.setText(AmountFormat.formatPlain((multiplier - 1) * 100));

		} else {

			et_percent.setText("50");
		}

		new AlertDialog.Builder(this)
			.setTitle(existingEntry == null ? "Add Busy Period" : "Edit Busy Period")
			.setView(container)
			.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						String label = et_label.getText().toString().trim();

						if (label.isEmpty()) {
							Toast.makeText(
								SeasonalCalendarActivity.this, "Enter a label",
								Toast.LENGTH_SHORT
							).show();
							return;
						}

						double percent;

						try {
							percent = Double.parseDouble(et_percent.getText().toString().trim());
						} catch (Exception e) {
							Toast.makeText(
								SeasonalCalendarActivity.this, "Enter a valid number",
								Toast.LENGTH_SHORT
							).show();
							return;
						}

						int month = spinner_month.getSelectedItemPosition() + 1;
						double multiplier = 1 + (percent / 100.0);

						if (existingEntry == null) {

							db.insertSeasonalCalendarEntry(label, month, multiplier);
							Toast.makeText(
								SeasonalCalendarActivity.this, "Busy period added",
								Toast.LENGTH_SHORT
							).show();

						} else {

							db.updateSeasonalCalendarEntry(
								(Integer) existingEntry.get("id"), label, month, multiplier);
							Toast.makeText(
								SeasonalCalendarActivity.this, "Busy period updated",
								Toast.LENGTH_SHORT
							).show();
						}

						loadEntries();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void confirmDeleteEntry(final int id) {

		new AlertDialog.Builder(this)
			.setTitle("Delete Busy Period")
			.setMessage("Are you sure you want to delete this busy period?")
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						db.deleteSeasonalCalendarEntry(id);
						loadEntries();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
