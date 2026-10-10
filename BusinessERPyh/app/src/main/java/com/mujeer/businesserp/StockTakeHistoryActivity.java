package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// List of every completed Stock Take session (see StockTakeActivity) -
// tapping one shows what, if anything, differed from expected stock when
// that count was taken. A session with zero discrepancies is a healthy
// count, not a failed one, so it still appears here with "No
// discrepancies" rather than being hidden.
// =====================
public class StockTakeHistoryActivity extends Activity {

	TextView tv_no_stock_take_history;
	LinearLayout container_stock_take_history;

	DatabaseHelper db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stock_take_history_activity);

		tv_no_stock_take_history = findViewById(R.id.tv_no_stock_take_history);
		container_stock_take_history = findViewById(R.id.container_stock_take_history);

		db = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadHistory();
	}

	private void loadHistory() {

		ArrayList<HashMap<String, Object>> sessions = db.getStockTakeHistory();

		container_stock_take_history.removeAllViews();

		if (sessions.isEmpty()) {

			tv_no_stock_take_history.setVisibility(View.VISIBLE);
			return;
		}

		tv_no_stock_take_history.setVisibility(View.GONE);

		for (final HashMap<String, Object> session : sessions) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_stock_take_history, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText((String) session.get("started_date"));

			int itemCount = (Integer) session.get("item_count");
			int discrepancyCount = (Integer) session.get("discrepancy_count");

			tv_detail.setText(itemCount + " item(s) in this session");

			tv_badge.setText(String.valueOf(discrepancyCount));
			tv_badge.setTextColor(getResources().getColor(
				discrepancyCount > 0 ? R.color.warning : R.color.success
			));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showDiscrepancies((Integer) session.get("id"));
					}
				}
			);

			container_stock_take_history.addView(view);
		}
	}

	private void showDiscrepancies(int sessionId) {

		ArrayList<HashMap<String, Object>> discrepancies =
			db.getStockTakeSessionDiscrepancies(sessionId);

		StringBuilder message = new StringBuilder();

		if (discrepancies.isEmpty()) {

			message.append("No discrepancies - every counted item matched its expected stock.");

		} else {

			for (HashMap<String, Object> row : discrepancies) {

				double expectedQty = (Double) row.get("expected_qty");
				double countedQty = (Double) row.get("counted_qty");

				message.append((String) row.get("name"))
					.append(" (").append((String) row.get("code")).append(")\n")
					.append("Expected ").append(AmountFormat.formatPlain(expectedQty))
					.append(" · Counted ").append(AmountFormat.formatPlain(countedQty))
					.append("\n\n");
			}
		}

		new AlertDialog.Builder(this)
			.setTitle("Stock Take Discrepancies")
			.setMessage(message.toString().trim())
			.setPositiveButton("Close", null)
			.show();
	}
}
