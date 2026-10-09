package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Lifetime sold % vs current stock % per shoe size (see
// DatabaseHelper.getSizeCurveAnalysis()) - a standing structural view,
// not tied to a period. A size with a big positive mismatch sells more
// than its share of stock (understocked - the next buy should lean
// toward it); a big negative mismatch sells less than its share (cash
// sitting idle in sizes that don't move).
// =====================
public class SizeCurveAnalysisReportActivity extends Activity {

	// Below this, a size's sold-vs-stock split is close enough that
	// flagging it either way would just be noise.
	private static final double BALANCED_THRESHOLD_POINTS = 3.0;

	private SimpleBarChartView chart_size_curve;

	private TextView tv_size_curve_empty;
	private LinearLayout container_size_curve;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.size_curve_analysis_report_activity);

		setTitle("Size-Curve Analysis");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Size-Curve Analysis",
			"Every shoe size's share of lifetime sales vs its share of current stock, parsed straight out of each item's name (Size is its own token in this shop's \"Shoes {Gender} {Type} {Sole} {Upper} {Design} {Color} {Size} - {Code}\" naming convention, not a separate variety group). A size selling more than its share of stock risks running out; one stocked more than it sells is cash tied up on a size that barely moves."
		);

		chart_size_curve = findViewById(R.id.chart_size_curve);

		tv_size_curve_empty = findViewById(R.id.tv_size_curve_empty);
		container_size_curve = findViewById(R.id.container_size_curve);

		db = new DatabaseHelper(this);

		loadReport();
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

					final ArrayList<HashMap<String, Object>> list = db.getSizeCurveAnalysis();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyReport(list);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyReport(ArrayList<HashMap<String, Object>> list) {

		container_size_curve.removeAllViews();

		if (list.isEmpty()) {

			tv_size_curve_empty.setVisibility(View.VISIBLE);
			chart_size_curve.setEntries(null);
			return;
		}

		tv_size_curve_empty.setVisibility(View.GONE);

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		for (final HashMap<String, Object> row : list) {

			String size = String.valueOf(row.get("size"));
			double mismatch = (Double) row.get("mismatch_points");

			int barColor =
				mismatch > BALANCED_THRESHOLD_POINTS ? getResources().getColor(R.color.danger) :
				mismatch < -BALANCED_THRESHOLD_POINTS ? getResources().getColor(R.color.accent) :
				getResources().getColor(R.color.success);

			chartEntries.add(new SimpleBarChartView.Entry(size, mismatch, barColor));

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_size_curve, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText("Size " + size);

			double soldPercent = (Double) row.get("sold_percent");
			double stockPercent = (Double) row.get("stock_percent");

			tv_detail.setText(
				"Sold " + AmountFormat.formatPlain(soldPercent) + "% of volume - Stock " +
				AmountFormat.formatPlain(stockPercent) + "% of stock"
			);

			if (mismatch > BALANCED_THRESHOLD_POINTS) {

				tv_badge.setText("+" + AmountFormat.formatPlain(mismatch) + "pp");
				tv_badge.setTextColor(getResources().getColor(R.color.danger));

			} else if (mismatch < -BALANCED_THRESHOLD_POINTS) {

				tv_badge.setText(AmountFormat.formatPlain(mismatch) + "pp");
				tv_badge.setTextColor(getResources().getColor(R.color.accent));

			} else {

				tv_badge.setText("Balanced");
				tv_badge.setTextColor(getResources().getColor(R.color.success));
			}

			container_size_curve.addView(view);
		}

		chart_size_curve.setEntries(chartEntries);
	}
}
