package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Today's real cash balance (DatabaseHelper.getCashBalance()) projected
// forward day by day using only Sales/Purchases already due on a future
// date (DatabaseHelper.getCashFlowForecast()) - never a prediction from
// history, so a day with nothing due simply carries the running balance
// forward unchanged. The horizon (how many days ahead to show) is the
// only control; there's no custom range here since "forecast" only
// means forward from today.
// =====================
public class CashFlowForecastReportActivity extends Activity {

	private static final int[] HORIZON_DAYS = {7, 14, 30, 60};

	private static final String[] HORIZON_LABELS = {
		"Next 7 Days", "Next 14 Days", "Next 30 Days", "Next 60 Days"
	};

	private static final int DEFAULT_HORIZON_INDEX = 2;

	private Spinner spinner_horizon;
	private ListView lv_cash_flow_forecast;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> forecastList =
		new ArrayList<HashMap<String, Object>>();

	private CashFlowForecastAdapter adapter;

	private int selectedHorizonDays = HORIZON_DAYS[DEFAULT_HORIZON_INDEX];

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cash_flow_forecast_report_activity);

		setTitle("Cash Flow Forecast");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Cash Flow Forecast", "Today's real cash balance projected forward using only Sales and Purchases already due on a future date - never a prediction, so a day with nothing due just carries the balance forward unchanged."
		);

		spinner_horizon = findViewById(R.id.spinner_horizon);
		lv_cash_flow_forecast = findViewById(R.id.lv_cash_flow_forecast);

		db = new DatabaseHelper(this);

		adapter = new CashFlowForecastAdapter(this, forecastList);
		lv_cash_flow_forecast.setAdapter(adapter);

		ArrayAdapter<String> horizonAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, HORIZON_LABELS
		);

		horizonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_horizon.setAdapter(horizonAdapter);

		spinner_horizon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedHorizonDays = HORIZON_DAYS[position];
					loadForecast();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		spinner_horizon.setSelection(DEFAULT_HORIZON_INDEX);

		loadForecast();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadForecast();
	}

	private void loadForecast() {

		final long myGeneration = ++loadGeneration;
		final int days = selectedHorizonDays;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getCashFlowForecast(days);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								forecastList.clear();
								forecastList.addAll(result);

								adapter.notifyDataSetChanged();
							}
						});
				}
			}).start();
	}
}
