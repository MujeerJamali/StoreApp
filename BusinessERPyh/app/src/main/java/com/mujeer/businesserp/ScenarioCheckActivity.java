package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// "What if I stocked X% more of category Y" rough profit estimate
// (approved feature list row #78) - see DatabaseHelper.
// getCategoryScenarioEstimate() for the actual math. Deliberately a
// one-shot calculator, not a live report: the user picks a category
// and a percentage, taps Calculate, and gets a straight-line estimate
// built from that category's own recent sales - nothing here commits
// anything or feeds back into the Reorder List.
// =====================
public class ScenarioCheckActivity extends Activity {

	private Spinner spinner_category;
	private EditText et_percent_more;
	private Button btn_calculate;

	private View container_result;
	private TextView tv_current_stock;
	private TextView tv_extra_units;
	private TextView tv_extra_cash_needed;
	private TextView tv_estimated_extra_profit;
	private TextView tv_basis;

	private DatabaseHelper db;
	private ArrayList<String> categories;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.scenario_check_activity);

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Scenario Check",
			"A rough profit estimate for stocking more of a category, built from that category's own recent sales speed and margin - a straight-line projection assuming the extra stock eventually sells at the same average rate, not a prediction."
		);

		spinner_category = findViewById(R.id.spinner_category);
		et_percent_more = findViewById(R.id.et_percent_more);
		btn_calculate = findViewById(R.id.btn_calculate);

		container_result = findViewById(R.id.container_result);
		tv_current_stock = findViewById(R.id.tv_current_stock);
		tv_extra_units = findViewById(R.id.tv_extra_units);
		tv_extra_cash_needed = findViewById(R.id.tv_extra_cash_needed);
		tv_estimated_extra_profit = findViewById(R.id.tv_estimated_extra_profit);
		tv_basis = findViewById(R.id.tv_basis);

		db = new DatabaseHelper(this);

		categories = db.getDistinctItemCategories();

		ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, categories
		);

		categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_category.setAdapter(categoryAdapter);

		btn_calculate.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					calculate();
				}
			}
		);
	}

	private void calculate() {

		if (categories.isEmpty()) {

			Toast.makeText(this, "No items to check yet", Toast.LENGTH_SHORT).show();
			return;
		}

		String category = categories.get(spinner_category.getSelectedItemPosition());

		double percentMore;

		try {
			percentMore = Double.parseDouble(et_percent_more.getText().toString().trim());
		} catch (Exception e) {
			Toast.makeText(this, "Enter a valid percentage", Toast.LENGTH_SHORT).show();
			return;
		}

		int velocityWindowDays = ReorderSettings.getVelocityWindowDays(this);

		HashMap<String, Object> estimate =
			db.getCategoryScenarioEstimate(category, velocityWindowDays, percentMore);

		double currentStock = (Double) estimate.get("current_stock");
		double extraUnits = (Double) estimate.get("extra_units");
		double extraCashNeeded = (Double) estimate.get("extra_cash_needed");
		Double estimatedExtraProfit = (Double) estimate.get("estimated_extra_profit");
		double qtySoldInWindow = (Double) estimate.get("qty_sold_in_window");

		tv_current_stock.setText(AmountFormat.formatPlain(currentStock));
		tv_extra_units.setText(AmountFormat.formatPlain(extraUnits));
		tv_extra_cash_needed.setText(AmountFormat.format(extraCashNeeded));

		if (estimatedExtraProfit == null) {

			tv_estimated_extra_profit.setText("Not enough sales history to estimate");
			tv_estimated_extra_profit.setTextColor(getResources().getColor(R.color.text_secondary));

		} else {

			tv_estimated_extra_profit.setText(AmountFormat.format(estimatedExtraProfit));
			tv_estimated_extra_profit.setTextColor(getResources().getColor(
				estimatedExtraProfit >= 0 ? R.color.success : R.color.danger));
		}

		tv_basis.setText(
			"Based on " + AmountFormat.formatPlain(qtySoldInWindow) + " units of \"" + category +
			"\" sold in the last " + velocityWindowDays + " days (Reorder Settings' sales-speed " +
			"window), stock-weighted average purchase price, and current stock of " +
			AmountFormat.formatPlain(currentStock) + "."
		);

		container_result.setVisibility(View.VISIBLE);
	}
}
