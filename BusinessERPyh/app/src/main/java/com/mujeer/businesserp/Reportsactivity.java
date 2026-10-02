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
	Button btn_report_net_profit;
	Button btn_report_stock_worth;
	Button btn_report_party_balances;
	Button btn_report_credit_due;
	Button btn_report_item_monthly_rank;
	Button btn_report_average_cart;
	Button btn_report_profit_split;
	Button btn_report_shoes_vs_non_shoes;
	Button btn_report_combo_stock;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reportsactivity);

		setTitle("Reports");

		btn_report_sales = findViewById(R.id.btn_report_sales);
		btn_report_party_sales = findViewById(R.id.btn_report_party_sales);
		btn_report_item_ranking = findViewById(R.id.btn_report_item_ranking);
		btn_report_party_ranking = findViewById(R.id.btn_report_party_ranking);
		btn_report_net_profit = findViewById(R.id.btn_report_net_profit);
		btn_report_stock_worth = findViewById(R.id.btn_report_stock_worth);
		btn_report_party_balances = findViewById(R.id.btn_report_party_balances);
		btn_report_credit_due = findViewById(R.id.btn_report_credit_due);
		btn_report_item_monthly_rank = findViewById(R.id.btn_report_item_monthly_rank);
		btn_report_average_cart = findViewById(R.id.btn_report_average_cart);
		btn_report_profit_split = findViewById(R.id.btn_report_profit_split);
		btn_report_shoes_vs_non_shoes = findViewById(R.id.btn_report_shoes_vs_non_shoes);
		btn_report_combo_stock = findViewById(R.id.btn_report_combo_stock);

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

		btn_report_net_profit.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						NetProfitReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_stock_worth.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						StockWorthReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_party_balances.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						PartyBalanceReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_credit_due.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						CreditDueReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_item_monthly_rank.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						ItemMonthlyRankReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_average_cart.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						AverageCartReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_profit_split.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						ProfitSplitReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_shoes_vs_non_shoes.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						ShoesVsNonShoesReportActivity.class
					);

					startActivity(intent);
				}
			});

		btn_report_combo_stock.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Reportsactivity.this,
						ComboStockReportActivity.class
					);

					startActivity(intent);
				}
			});
	}
}
