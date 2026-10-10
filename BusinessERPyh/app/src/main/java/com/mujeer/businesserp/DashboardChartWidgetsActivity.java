package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Picker for the "Live mini-chart widget cards" Dashboard feature - the
// user turns individual reports on/off and reorders them here; MainActivity
// then renders each turned-on report's real chart (via
// DashboardChartWidgetLoader) directly on the Dashboard, not just a text
// shortcut (see DashboardFavorites/ReportFavorites for that older,
// separate mechanism). Same checkbox + up/down-reorder screen shape as
// DashboardCustomizeActivity, just driven by DashboardChartWidgetLoader's
// SUPPORTED_KEYS/DashboardChartWidgets instead of the three fixed
// glanceable cards - see those two classes' own comments for why a
// report widget defaults to OFF where a glanceable card defaults to ON.
// =====================
public class DashboardChartWidgetsActivity extends Activity {

	LinearLayout container_chart_widgets;
	Button btn_save_chart_widgets;

	private ArrayList<String> widgetOrder;

	// The single source of truth for each widget's checked state while
	// this screen is open - renderRows() re-inflates rows on every
	// reorder, so the checkbox state has to live here rather than in
	// whatever CheckBox view happens to exist at a given moment.
	private final HashMap<String, Boolean> checkedStates = new HashMap<String, Boolean>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dashboard_chart_widgets_activity);

		setTitle("Dashboard Chart Widgets");

		container_chart_widgets = findViewById(R.id.container_chart_widgets);
		btn_save_chart_widgets = findViewById(R.id.btn_save_chart_widgets);

		widgetOrder = DashboardChartWidgets.getOrder(this);

		for (String key : widgetOrder) {
			checkedStates.put(key, DashboardChartWidgets.isVisible(this, key));
		}

		renderRows();

		btn_save_chart_widgets.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					DashboardChartWidgets.saveOrder(DashboardChartWidgetsActivity.this, widgetOrder);

					for (String key : widgetOrder) {

						DashboardChartWidgets.setVisible(
							DashboardChartWidgetsActivity.this,
							key,
							Boolean.TRUE.equals(checkedStates.get(key))
						);
					}

					Toast.makeText(
						DashboardChartWidgetsActivity.this,
						"Dashboard chart widgets saved",
						Toast.LENGTH_SHORT
					).show();

					finish();
				}
			});
	}

	private void renderRows() {

		container_chart_widgets.removeAllViews();

		for (int i = 0; i < widgetOrder.size(); i++) {

			final int position = i;
			final String key = widgetOrder.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.dashboard_customize_row, container_chart_widgets, false
			);

			CheckBox cb = row.findViewById(R.id.cb_card_visible);
			TextView tv_label = row.findViewById(R.id.tv_card_label);
			TextView btn_up = row.findViewById(R.id.btn_card_up);
			TextView btn_down = row.findViewById(R.id.btn_card_down);

			tv_label.setText(DashboardChartWidgetLoader.labelFor(key));
			cb.setChecked(Boolean.TRUE.equals(checkedStates.get(key)));

			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						checkedStates.put(key, isChecked);
					}
				});

			btn_up.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
			btn_down.setVisibility(position == widgetOrder.size() - 1 ? View.INVISIBLE : View.VISIBLE);

			btn_up.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						moveWidget(position, position - 1);
					}
				});

			btn_down.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						moveWidget(position, position + 1);
					}
				});

			container_chart_widgets.addView(row);
		}
	}

	private void moveWidget(int fromPosition, int toPosition) {

		String moved = widgetOrder.remove(fromPosition);
		widgetOrder.add(toPosition, moved);

		renderRows();
	}
}
