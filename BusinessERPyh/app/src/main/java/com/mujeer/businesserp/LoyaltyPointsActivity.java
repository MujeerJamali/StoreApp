package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every party with a non-zero loyalty points balance, highest first
// (see DatabaseHelper.getLoyaltyRanking()) - 1 point per
// LOYALTY_POINTS_PER_RUPEES spent on a Sale, earned automatically
// (Transactioneditactivity, right after a new Sale saves) and
// adjustable by hand from a party's own screen. Tapping a row opens
// that party, where points can be viewed/adjusted further.
// =====================
public class LoyaltyPointsActivity extends Activity {

	private TextView tv_loyalty_empty;
	private LinearLayout container_loyalty_ranking;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.loyalty_points_activity);

		setTitle("Loyalty Points");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Loyalty Points",
			"Every named customer earns 1 loyalty point per " +
			DatabaseHelper.LOYALTY_POINTS_PER_RUPEES +
			" spent on a Sale (the \"Cash Sale\" placeholder party never earns any, since it isn't a trackable customer). Points earned stay earned even if the Sale is later edited or deleted - a genuine correction is a manual adjustment from the party's own screen instead. Crossing a milestone (100/250/500/1000/2500/5000/10000 points) shows a one-time notice right after the Sale that crossed it."
		);

		tv_loyalty_empty = findViewById(R.id.tv_loyalty_empty);
		container_loyalty_ranking = findViewById(R.id.container_loyalty_ranking);

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

					final ArrayList<HashMap<String, Object>> list = db.getLoyaltyRanking();

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

		container_loyalty_ranking.removeAllViews();

		if (list.isEmpty()) {

			tv_loyalty_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_loyalty_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_loyalty_ranking, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));
			tv_detail.setText("Tap to view party");

			int points = (Integer) row.get("points");

			tv_badge.setText(points + " pts");
			tv_badge.setTextColor(
				getResources().getColor(points >= 0 ? R.color.success : R.color.danger)
			);

			final int partyId = (Integer) row.get("party_id");

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							LoyaltyPointsActivity.this, Partyviewactivity.class
						);

						intent.putExtra("party_id", partyId);
						startActivity(intent);
					}
				}
			);

			container_loyalty_ranking.addView(view);
		}
	}
}
