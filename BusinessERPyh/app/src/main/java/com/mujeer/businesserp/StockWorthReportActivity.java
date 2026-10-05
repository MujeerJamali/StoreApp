package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Current stock worth (quantity * purchase_price), split into shoes/
// non-shoes by the app-wide "name starts with 'Shoe'" rule. Always a
// snapshot of right now - see DatabaseHelper.getStockWorthSummary()
// for why this can't be shown for past periods.
// =====================
public class StockWorthReportActivity extends Activity {

	private TextView tv_total_worth;
	private TextView tv_shoes_worth;
	private TextView tv_shoes_count;
	private TextView tv_non_shoes_worth;
	private TextView tv_non_shoes_count;
	private SimpleBarChartView chart_stock_worth;

	private DatabaseHelper db;

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stock_worth_report_activity);

		setTitle("Stock Worth");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Stock Worth", "Current stock quantity times purchase price, as of right now. There's no historical stock-level record to look back further."
		);

		tv_total_worth = findViewById(R.id.tv_total_worth);
		tv_shoes_worth = findViewById(R.id.tv_shoes_worth);
		tv_shoes_count = findViewById(R.id.tv_shoes_count);
		tv_non_shoes_worth = findViewById(R.id.tv_non_shoes_worth);
		tv_non_shoes_count = findViewById(R.id.tv_non_shoes_count);
		chart_stock_worth = findViewById(R.id.chart_stock_worth);

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

					final HashMap<String, Object> summary = db.getStockWorthSummary();

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

		double totalWorth = (Double) summary.get("total_worth");
		double shoesWorth = (Double) summary.get("shoes_worth");
		double nonShoesWorth = (Double) summary.get("non_shoes_worth");
		int shoesCount = (Integer) summary.get("shoes_count");
		int nonShoesCount = (Integer) summary.get("non_shoes_count");

		tv_total_worth.setText(AmountFormat.format(totalWorth));
		tv_shoes_worth.setText(AmountFormat.format(shoesWorth));
		tv_non_shoes_worth.setText(AmountFormat.format(nonShoesWorth));

		tv_shoes_count.setText(shoesCount + (shoesCount == 1 ? " item" : " items"));
		tv_non_shoes_count.setText(nonShoesCount + (nonShoesCount == 1 ? " item" : " items"));

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();
		chartEntries.add(new SimpleBarChartView.Entry("Shoes", shoesWorth, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry("Non-Shoes", nonShoesWorth, getResources().getColor(R.color.primary)));
		chart_stock_worth.setEntries(chartEntries);
	}
}
