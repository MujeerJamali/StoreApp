package com.mujeer.businesserp;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// =====================
// Record of Changes - a permanent, view-only log of every database-
// changing action taken in the app (see DatabaseHelper.logAction()/
// getActionLogPage()), separate from Recently Deleted: that screen is
// for undoing/restoring a specific Sale or Purchase; this one is a
// plain audit trail with no restore/undo/delete UI at all - a row here
// can never be edited or removed, by design. Paginated 30 rows at a
// time, same "Load More" convention as Reorder Learning History/
// Itemviewactivity's own transaction history, since this table only
// grows for as long as the shop uses the app. A "Show" multi-select
// filter (same AlertDialog-checkbox pattern as CashActivity's own Show
// filter - a real Spinner can't multi-select) narrows the list to any
// combination of DatabaseHelper.ACTION_LOG_ENTITY_TYPES. Each row also
// shows the running cash balance right after it happened
// (cash_balance_after, captured once at log time - not recomputed),
// and tapping a row opens a detail dialog with the full change plus
// its Previous/New cash balance either side of it.
// =====================
public class ActionLogActivity extends Activity {

	private static final int PAGE_SIZE = 30;

	private static final HashSet<String> POSITIVE_ACTIONS = new HashSet<>();
	private static final HashSet<String> NEGATIVE_ACTIONS = new HashSet<>();

	static {
		POSITIVE_ACTIONS.add("Created");
		POSITIVE_ACTIONS.add("Restored");

		NEGATIVE_ACTIONS.add("Deleted");
		NEGATIVE_ACTIONS.add("Deleted Permanently");
		NEGATIVE_ACTIONS.add("Deleted Draft");
	}

	private TextView tv_action_log_empty;
	private TextView tv_type_filter;
	private LinearLayout container_action_log;
	private Button btn_load_more_action_log;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> logList = new ArrayList<>();

	// All types shown by default - nothing is filtered out until the
	// user deliberately narrows it down.
	private final HashSet<String> selectedEntityTypes = new HashSet<>();

	private int totalCount = 0;

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.action_log_activity);

		setTitle("Record of Changes");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Record of Changes",
			"Every action that changed your data - Parties, Items, Purchases, Sales, Payments, Expenses, Cash Adjustments, Cost Items, Varieties, Wanted Items, Recurring Expenses, the Seasonal Calendar, Drafts, and restoring/undoing from Recently Deleted - with exactly what it was, when it happened, and the cash balance right after. Tap a row for its full detail, including the cash balance just before it. This is a permanent audit trail: nothing here can be edited or deleted, by design. For undoing or restoring a specific Sale/Purchase, use Recently Deleted instead - this screen only records that it happened."
		);

		tv_action_log_empty = findViewById(R.id.tv_action_log_empty);
		tv_type_filter = findViewById(R.id.tv_action_log_type_filter);
		container_action_log = findViewById(R.id.container_action_log);
		btn_load_more_action_log = findViewById(R.id.btn_load_more_action_log);

		for (String type : DatabaseHelper.ACTION_LOG_ENTITY_TYPES) {
			selectedEntityTypes.add(type);
		}

		db = new DatabaseHelper(this);

		updateTypeFilterText();

		tv_type_filter.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptTypeFilter();
				}
			}
		);

		btn_load_more_action_log.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					loadMore();
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

	// null means "no filter" - passed to DatabaseHelper whenever every
	// type is selected, so it skips building an IN clause at all.
	private HashSet<String> filterForQuery() {

		if (selectedEntityTypes.size() == DatabaseHelper.ACTION_LOG_ENTITY_TYPES.length) {
			return null;
		}

		return selectedEntityTypes;
	}

	private void loadFirstPage() {

		final long myGeneration = ++loadGeneration;
		final HashSet<String> filter = filterForQuery();

		new Thread(new Runnable() {
				@Override
				public void run() {

					final int count = db.getActionLogCount(filter);
					final ArrayList<HashMap<String, Object>> page =
						db.getActionLogPage(PAGE_SIZE, 0, filter);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								totalCount = count;
								logList.clear();
								logList.addAll(page);

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
		final int offset = logList.size();
		final HashSet<String> filter = filterForQuery();

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> page =
						db.getActionLogPage(PAGE_SIZE, offset, filter);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								logList.addAll(page);
								renderList();
							}
						}
					);
				}
			}
		).start();
	}

	private void renderList() {

		container_action_log.removeAllViews();

		if (logList.isEmpty()) {

			tv_action_log_empty.setVisibility(View.VISIBLE);
			btn_load_more_action_log.setVisibility(View.GONE);
			return;
		}

		tv_action_log_empty.setVisibility(View.GONE);

		LayoutInflater inflater = LayoutInflater.from(this);

		for (final HashMap<String, Object> row : logList) {

			View view = inflater.inflate(R.layout.action_log_row, container_action_log, false);

			TextView tv_action = view.findViewById(R.id.tv_row_action);
			TextView tv_timestamp = view.findViewById(R.id.tv_row_timestamp);
			TextView tv_label = view.findViewById(R.id.tv_row_label);
			TextView tv_cash_balance = view.findViewById(R.id.tv_row_cash_balance);

			String actionType = String.valueOf(row.get("action_type"));
			String entityType = String.valueOf(row.get("entity_type"));

			tv_action.setText(actionType + " " + entityType);
			tv_action.setTextColor(getResources().getColor(actionColor(actionType)));

			tv_label.setText(String.valueOf(row.get("label")));

			String date = String.valueOf(row.get("action_date"));
			String time = String.valueOf(row.get("action_time"));

			tv_timestamp.setText(RelativeDate.format(date) + " at " + time);

			Double cashBalance = (Double) row.get("cash_balance_after");

			tv_cash_balance.setText(
				"Cash after: " + (cashBalance != null ? AmountFormat.format(cashBalance) : "Not available")
			);

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showDetail(row);
					}
				}
			);

			container_action_log.addView(view);
		}

		btn_load_more_action_log.setVisibility(
			logList.size() < totalCount ? View.VISIBLE : View.GONE
		);
	}

	private int actionColor(String actionType) {

		if (POSITIVE_ACTIONS.contains(actionType)) {
			return R.color.success;
		}

		if (NEGATIVE_ACTIONS.contains(actionType)) {
			return R.color.danger;
		}

		return R.color.primary;
	}

	// Excel-style multi-select, but as a plain AlertDialog with checkbox
	// items rather than a dropdown - a real Spinner can only ever select
	// one row at a time, so it can't drive this.
	private void promptTypeFilter() {

		final String[] types = DatabaseHelper.ACTION_LOG_ENTITY_TYPES;
		final boolean[] checked = new boolean[types.length];

		for (int i = 0; i < types.length; i++) {
			checked[i] = selectedEntityTypes.contains(types[i]);
		}

		new AlertDialog.Builder(this)
			.setTitle("Show Change Types")
			.setMultiChoiceItems(types, checked, new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						checked[which] = isChecked;
					}
				}
			)
			.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						selectedEntityTypes.clear();

						for (int i = 0; i < types.length; i++) {
							if (checked[i]) {
								selectedEntityTypes.add(types[i]);
							}
						}

						if (selectedEntityTypes.isEmpty()) {

							for (String type : types) {
								selectedEntityTypes.add(type);
							}

							Toast.makeText(
								ActionLogActivity.this,
								"At least one type must stay selected - showing all",
								Toast.LENGTH_SHORT
							).show();
						}

						updateTypeFilterText();
						loadFirstPage();
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void updateTypeFilterText() {

		if (selectedEntityTypes.size() == DatabaseHelper.ACTION_LOG_ENTITY_TYPES.length) {

			tv_type_filter.setText("All Types");

		} else if (selectedEntityTypes.size() == 1) {

			tv_type_filter.setText(selectedEntityTypes.iterator().next());

		} else {

			tv_type_filter.setText(selectedEntityTypes.size() + " Types Selected");
		}
	}

	// Looks up the previous row's own cash_balance_after in the
	// background (a single indexed lookup, but every DB read in this
	// app runs off the UI thread), then shows the detail dialog.
	private void showDetail(final HashMap<String, Object> row) {

		final int id = (Integer) row.get("id");

		new Thread(new Runnable() {
				@Override
				public void run() {

					final Double previousBalance = db.getPreviousCashBalance(id);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (isFinishing()) {
									return;
								}

								renderDetailDialog(row, previousBalance);
							}
						}
					);
				}
			}
		).start();
	}

	private void renderDetailDialog(HashMap<String, Object> row, Double previousBalance) {

		View view = getLayoutInflater().inflate(R.layout.dialog_action_log_detail, null);

		TextView tv_action = view.findViewById(R.id.tv_detail_action);
		TextView tv_timestamp = view.findViewById(R.id.tv_detail_timestamp);
		TextView tv_label = view.findViewById(R.id.tv_detail_label);
		TextView tv_previous_balance = view.findViewById(R.id.tv_detail_previous_balance);
		TextView tv_new_balance = view.findViewById(R.id.tv_detail_new_balance);
		LinearLayout row_change = view.findViewById(R.id.row_detail_change);
		TextView tv_change = view.findViewById(R.id.tv_detail_change);

		String actionType = String.valueOf(row.get("action_type"));
		String entityType = String.valueOf(row.get("entity_type"));

		tv_action.setText(actionType + " " + entityType);
		tv_action.setTextColor(getResources().getColor(actionColor(actionType)));

		String date = String.valueOf(row.get("action_date"));
		String time = String.valueOf(row.get("action_time"));

		tv_timestamp.setText(RelativeDate.format(date) + " at " + time);

		tv_label.setText(String.valueOf(row.get("label")));

		Double newBalance = (Double) row.get("cash_balance_after");

		tv_previous_balance.setText(
			previousBalance != null ? AmountFormat.format(previousBalance) : "Not available"
		);

		tv_new_balance.setText(
			newBalance != null ? AmountFormat.format(newBalance) : "Not available"
		);

		if (previousBalance != null && newBalance != null) {

			double change = newBalance - previousBalance;

			tv_change.setText(
				(change >= 0 ? "+" : "") + AmountFormat.format(change)
			);

			tv_change.setTextColor(
				getResources().getColor(change < 0 ? R.color.danger : R.color.success)
			);

		} else {

			row_change.setVisibility(View.GONE);
		}

		new AlertDialog.Builder(this)
			.setView(view)
			.setPositiveButton("Close", null)
			.show();
	}
}
