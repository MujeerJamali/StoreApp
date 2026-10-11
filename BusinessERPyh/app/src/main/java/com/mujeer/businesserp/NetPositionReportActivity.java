package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.HashMap;

// =====================
// Cash in hand, plus running stock value, minus Debit (what's owed to
// parties) - see DatabaseHelper.getNetPositionSummary() for exactly
// what each figure includes and why. A single aggregate number with a
// 3-line breakdown, not a list - no chart here, same as Stock Worth's
// own "single number" sections.
// =====================
public class NetPositionReportActivity extends Activity {

	private TextView tv_net_position;
	private TextView tv_cash;
	private TextView tv_running_stock_value;
	private TextView tv_debit;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_position_report_activity);

		setTitle("Net Position");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Net Position",
			"Cash in hand, plus the value of your running stock (everything NOT sitting dead/slow-moving for 60+ effective days, or never sold - same cutoff Dead Stock Aging uses), valued at purchase price only (no landed-cost extra), minus Debit - what you currently owe parties right now (the sum of every negative party balance, i.e. Payable). Receivables (what parties owe you) are deliberately left out - this is a conservative \"could you cover what you owe right now\" figure, not a full net-worth statement. Always a snapshot of right now; there's no historical record to look back further."
		);

		tv_net_position = findViewById(R.id.tv_net_position);
		tv_cash = findViewById(R.id.tv_cash);
		tv_running_stock_value = findViewById(R.id.tv_running_stock_value);
		tv_debit = findViewById(R.id.tv_debit);

		db = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void loadReport() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> summary = db.getNetPositionSummary(NetPositionReportActivity.this);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyReport(summary);
							}
						});
				}
			}).start();
	}

	private void applyReport(HashMap<String, Object> summary) {

		double cash = (Double) summary.get("cash");
		double runningStockValue = (Double) summary.get("running_stock_value");
		double debit = (Double) summary.get("debit");
		double netPosition = (Double) summary.get("net_position");

		tv_cash.setText(AmountFormat.format(cash));
		tv_running_stock_value.setText(AmountFormat.format(runningStockValue));
		tv_debit.setText(AmountFormat.format(debit));

		tv_net_position.setText(AmountFormat.format(netPosition));
		tv_net_position.setTextColor(
			getResources().getColor(netPosition < 0 ? R.color.danger : R.color.primary)
		);
	}
}
