package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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
// grows for as long as the shop uses the app.
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
	private LinearLayout container_action_log;
	private Button btn_load_more_action_log;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> logList = new ArrayList<>();

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
			"Every action that changed your data - Parties, Items, Purchases, Sales, Payments, Expenses, Cash Adjustments, Cost Items, Varieties, Wanted Items, Recurring Expenses, the Seasonal Calendar, Drafts, and restoring/undoing from Recently Deleted - with exactly what it was and when it happened. This is a permanent audit trail: nothing here can be edited or deleted, by design. For undoing or restoring a specific Sale/Purchase, use Recently Deleted instead - this screen only records that it happened."
		);

		tv_action_log_empty = findViewById(R.id.tv_action_log_empty);
		container_action_log = findViewById(R.id.container_action_log);
		btn_load_more_action_log = findViewById(R.id.btn_load_more_action_log);

		db = new DatabaseHelper(this);

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

	private void loadFirstPage() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final int count = db.getActionLogCount();
					final ArrayList<HashMap<String, Object>> page = db.getActionLogPage(PAGE_SIZE, 0);

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

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> page =
						db.getActionLogPage(PAGE_SIZE, offset);

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

		for (HashMap<String, Object> row : logList) {

			View view = inflater.inflate(R.layout.action_log_row, container_action_log, false);

			TextView tv_action = view.findViewById(R.id.tv_row_action);
			TextView tv_timestamp = view.findViewById(R.id.tv_row_timestamp);
			TextView tv_label = view.findViewById(R.id.tv_row_label);

			String actionType = String.valueOf(row.get("action_type"));
			String entityType = String.valueOf(row.get("entity_type"));

			tv_action.setText(actionType + " " + entityType);
			tv_action.setTextColor(getResources().getColor(actionColor(actionType)));

			tv_label.setText(String.valueOf(row.get("label")));

			String date = String.valueOf(row.get("action_date"));
			String time = String.valueOf(row.get("action_time"));

			tv_timestamp.setText(RelativeDate.format(date) + " at " + time);

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
}
