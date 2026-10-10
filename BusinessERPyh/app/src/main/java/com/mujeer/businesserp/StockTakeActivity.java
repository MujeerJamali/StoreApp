package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Stock take / physical count reconciliation mode (approved feature list
// row #81) - at most one session open at a time. Starting one snapshots
// every active item's (and variety combo's) current balance as its
// "expected" quantity; the user then walks the shop tapping each row to
// enter what they actually counted. Finishing applies every counted
// line's difference as a real stock adjustment (same mechanism a
// Purchase/Sale line already uses) and files the session for later
// review on the History screen - see DatabaseHelper's STOCK TAKE
// section.
// =====================
public class StockTakeActivity extends Activity {

	LinearLayout group_no_session;
	LinearLayout group_in_session;

	Button btn_start_stock_take;
	Button btn_stock_take_history;
	Button btn_finish_stock_take;
	Button btn_cancel_stock_take;

	TextView tv_stock_take_progress;
	LinearLayout container_stock_take_lines;

	DatabaseHelper db;

	Integer openSessionId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stock_take_activity);

		group_no_session = findViewById(R.id.group_no_session);
		group_in_session = findViewById(R.id.group_in_session);

		btn_start_stock_take = findViewById(R.id.btn_start_stock_take);
		btn_stock_take_history = findViewById(R.id.btn_stock_take_history);
		btn_finish_stock_take = findViewById(R.id.btn_finish_stock_take);
		btn_cancel_stock_take = findViewById(R.id.btn_cancel_stock_take);

		tv_stock_take_progress = findViewById(R.id.tv_stock_take_progress);
		container_stock_take_lines = findViewById(R.id.container_stock_take_lines);

		db = new DatabaseHelper(this);

		btn_start_stock_take.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startStockTake();
				}
			}
		);

		btn_stock_take_history.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						StockTakeActivity.this, StockTakeHistoryActivity.class));
				}
			}
		);

		btn_finish_stock_take.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmFinishStockTake();
				}
			}
		);

		btn_cancel_stock_take.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmCancelStockTake();
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadState();
	}

	private void loadState() {

		openSessionId = db.getOpenStockTakeSession();

		if (openSessionId == null) {

			group_no_session.setVisibility(View.VISIBLE);
			group_in_session.setVisibility(View.GONE);

		} else {

			group_no_session.setVisibility(View.GONE);
			group_in_session.setVisibility(View.VISIBLE);

			loadLines();
		}
	}

	private void startStockTake() {

		long sessionId = db.startStockTake();
		openSessionId = (int) sessionId;

		group_no_session.setVisibility(View.GONE);
		group_in_session.setVisibility(View.VISIBLE);

		loadLines();
	}

	private void loadLines() {

		HashMap<String, Object> progress = db.getStockTakeProgress(openSessionId);

		tv_stock_take_progress.setText(
			progress.get("counted") + " of " + progress.get("total") + " counted"
		);

		ArrayList<HashMap<String, Object>> lines = db.getStockTakeLines(openSessionId);

		container_stock_take_lines.removeAllViews();

		for (final HashMap<String, Object> line : lines) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_stock_take_lines, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText((String) line.get("name"));

			double expectedQty = (Double) line.get("expected_qty");
			boolean counted = (Boolean) line.get("counted");

			if (!counted) {

				tv_detail.setText("Expected " + AmountFormat.formatPlain(expectedQty));
				tv_badge.setText("Count");
				tv_badge.setTextColor(getResources().getColor(R.color.primary));

			} else {

				double countedQty = (Double) line.get("counted_qty");
				double diff = countedQty - expectedQty;

				tv_detail.setText(
					"Expected " + AmountFormat.formatPlain(expectedQty) +
					" · Counted " + AmountFormat.formatPlain(countedQty)
				);

				if (diff == 0) {

					tv_badge.setText("Match");
					tv_badge.setTextColor(getResources().getColor(R.color.success));

				} else {

					String sign = diff > 0 ? "+" : "";
					tv_badge.setText(sign + AmountFormat.formatPlain(diff));
					tv_badge.setTextColor(getResources().getColor(R.color.warning));
				}
			}

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						promptCount(line);
					}
				}
			);

			container_stock_take_lines.addView(view);
		}
	}

	private void promptCount(HashMap<String, Object> line) {

		final int lineId = (Integer) line.get("id");
		String name = (String) line.get("name");
		double expectedQty = (Double) line.get("expected_qty");

		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

		if (line.get("counted_qty") != null) {
			input.setText(AmountFormat.formatPlain((Double) line.get("counted_qty")));
		} else {
			input.setHint(AmountFormat.formatPlain(expectedQty));
		}

		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		input.setPadding(pad, pad, pad, pad);

		new AlertDialog.Builder(this)
			.setTitle("Count: " + name)
			.setMessage("Expected stock: " + AmountFormat.formatPlain(expectedQty))
			.setView(input)
			.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						double countedQty;

						try {
							countedQty = Double.parseDouble(input.getText().toString().trim());
						} catch (Exception e) {
							Toast.makeText(
								StockTakeActivity.this,
								"Enter a valid number", Toast.LENGTH_SHORT
							).show();
							return;
						}

						if (countedQty < 0) {
							Toast.makeText(
								StockTakeActivity.this,
								"Enter a number 0 or more", Toast.LENGTH_SHORT
							).show();
							return;
						}

						db.setStockTakeLineCount(lineId, countedQty);
						loadLines();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void confirmFinishStockTake() {

		new AlertDialog.Builder(this)
			.setTitle("Finish Stock Take")
			.setMessage(
				"Every counted item's difference from its expected stock will be applied " +
				"as a real stock adjustment. An item you never got to counting is left " +
				"untouched. This can't be undone. Continue?"
			)
			.setPositiveButton("Finish", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finishStockTake();
					}
				})
			.setNegativeButton("Keep Counting", null)
			.show();
	}

	private void finishStockTake() {

		HashMap<String, Object> result = db.finishStockTake(openSessionId);

		int discrepancyCount = (Integer) result.get("discrepancy_count");

		Toast.makeText(
			this,
			"Stock take finished - " + discrepancyCount + " item(s) adjusted",
			Toast.LENGTH_LONG
		).show();

		loadState();
	}

	private void confirmCancelStockTake() {

		new AlertDialog.Builder(this)
			.setTitle("Cancel Stock Take")
			.setMessage(
				"Every count entered so far will be discarded and no stock adjustment " +
				"will be applied. Continue?"
			)
			.setPositiveButton("Cancel Stock Take", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						db.cancelStockTake(openSessionId);
						loadState();
					}
				})
			.setNegativeButton("Keep Session", null)
			.show();
	}
}
