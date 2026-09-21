package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class Reportsactivity extends Activity {

	Button btn_report_sales;
	Button btn_report_party_sales;
	Button btn_report_item_ranking;
	Button btn_report_party_ranking;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reportsactivity);

		setTitle("Reports");

		btn_report_sales = findViewById(R.id.btn_report_sales);
		btn_report_party_sales = findViewById(R.id.btn_report_party_sales);
		btn_report_item_ranking = findViewById(R.id.btn_report_item_ranking);
		btn_report_party_ranking = findViewById(R.id.btn_report_party_ranking);

		btn_report_sales.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						Salesreportactivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_party_sales.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						Partysalesreportactivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_item_ranking.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						Itemrankingreportactivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_party_ranking.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						Partyrankingreportactivity.class
					);

					startActivity(intent);
				}
			});
	}
}
