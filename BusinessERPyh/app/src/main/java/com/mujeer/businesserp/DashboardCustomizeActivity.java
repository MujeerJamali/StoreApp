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
// Customizable dashboard (approved feature "pick which cards show
// first") - reorders/hides the Dashboard's three glanceable info cards
// (Favorites/Cash Summary/Sales Trend; see DashboardCardOrder). The
// Modules/Tools navigation cards below them are deliberately not
// included here - hiding core navigation would risk the user locking
// themselves out of the rest of the app.
// =====================
public class DashboardCustomizeActivity extends Activity {

	LinearLayout container_dashboard_cards;
	Button btn_save_dashboard_order;

	private ArrayList<String> cardOrder;

	// The single source of truth for each card's checked state while
	// this screen is open - renderRows() re-inflates rows on every
	// reorder, so the checkbox state has to live here rather than in
	// whatever CheckBox view happens to exist at a given moment.
	private final HashMap<String, Boolean> checkedStates = new HashMap<String, Boolean>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dashboard_customize_activity);

		setTitle("Customize Dashboard");

		container_dashboard_cards = findViewById(R.id.container_dashboard_cards);
		btn_save_dashboard_order = findViewById(R.id.btn_save_dashboard_order);

		cardOrder = DashboardCardOrder.getOrder(this);

		for (String cardKey : cardOrder) {
			checkedStates.put(cardKey, DashboardCardOrder.isVisible(this, cardKey));
		}

		renderRows();

		btn_save_dashboard_order.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					DashboardCardOrder.saveOrder(DashboardCustomizeActivity.this, cardOrder);

					for (String cardKey : cardOrder) {

						DashboardCardOrder.setVisible(
							DashboardCustomizeActivity.this,
							cardKey,
							Boolean.TRUE.equals(checkedStates.get(cardKey))
						);
					}

					Toast.makeText(
						DashboardCustomizeActivity.this,
						"Dashboard layout saved",
						Toast.LENGTH_SHORT
					).show();

					finish();
				}
			});
	}

	private void renderRows() {

		container_dashboard_cards.removeAllViews();

		for (int i = 0; i < cardOrder.size(); i++) {

			final int position = i;
			final String cardKey = cardOrder.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.dashboard_customize_row, container_dashboard_cards, false
			);

			CheckBox cb = row.findViewById(R.id.cb_card_visible);
			TextView tv_label = row.findViewById(R.id.tv_card_label);
			TextView btn_up = row.findViewById(R.id.btn_card_up);
			TextView btn_down = row.findViewById(R.id.btn_card_down);

			tv_label.setText(labelFor(cardKey));
			cb.setChecked(Boolean.TRUE.equals(checkedStates.get(cardKey)));

			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						checkedStates.put(cardKey, isChecked);
					}
				});

			btn_up.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
			btn_down.setVisibility(position == cardOrder.size() - 1 ? View.INVISIBLE : View.VISIBLE);

			btn_up.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						moveCard(position, position - 1);
					}
				});

			btn_down.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						moveCard(position, position + 1);
					}
				});

			container_dashboard_cards.addView(row);
		}
	}

	private void moveCard(int fromPosition, int toPosition) {

		String moved = cardOrder.remove(fromPosition);
		cardOrder.add(toPosition, moved);

		renderRows();
	}

	private String labelFor(String cardKey) {

		for (int i = 0; i < DashboardCardOrder.ALL_CARDS.length; i++) {

			if (DashboardCardOrder.ALL_CARDS[i].equals(cardKey)) {
				return DashboardCardOrder.ALL_CARD_LABELS[i];
			}
		}

		return cardKey;
	}
}
