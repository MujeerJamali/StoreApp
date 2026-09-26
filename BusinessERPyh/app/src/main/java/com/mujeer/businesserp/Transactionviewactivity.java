package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Transactionviewactivity extends Activity {
	


    DatabaseHelper db;

    TextView tv_purchase_code;
	TextView tv_title;
    TextView tv_purchase_party;
    TextView tv_purchase_date;
    TextView tv_purchase_invoice;
    TextView tv_purchase_total;
    TextView tv_purchase_paid;
    TextView tv_purchase_notes;

    ListView lv_transaction_items;

    Button btn_delete_purchase;
	Button btn_edit_purchase;
	Button btn_bulk_import_purchase;

    private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	int transactionType = TYPE_PURCHASE;
	int transactionId = -1;

    ArrayList<HashMap<String,Object>> transactionItems;

    TransactionItemAdapter transactionItemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transactionviewactivity);

        tv_purchase_code = findViewById(R.id.tv_purchase_code);
        tv_purchase_party = findViewById(R.id.tv_purchase_party);
        tv_purchase_date = findViewById(R.id.tv_purchase_date);
        tv_purchase_invoice = findViewById(R.id.tv_purchase_invoice);
        tv_purchase_total = findViewById(R.id.tv_purchase_total);
        tv_purchase_paid = findViewById(R.id.tv_purchase_paid);
        tv_purchase_notes = findViewById(R.id.tv_purchase_notes);

        lv_transaction_items = findViewById(R.id.lv_purchase_items);
        btn_delete_purchase = findViewById(R.id.btn_delete_purchase);
		btn_edit_purchase = findViewById(R.id.btn_edit_purchase);
		btn_bulk_import_purchase = findViewById(R.id.btn_bulk_import_purchase);
		tv_title = findViewById(R.id.tv_title);

        db = new DatabaseHelper(this);

        transactionType = getIntent().getIntExtra(
			"transaction_type",
			TYPE_PURCHASE
		);
		
		if (transactionType == TYPE_PURCHASE) {

			btn_edit_purchase.setText("Edit Purchase");

			btn_delete_purchase.setText("Delete Purchase");
			
			tv_title.setText("Perchase Details");

		} else {

			btn_edit_purchase.setText("Edit Sale");

			btn_delete_purchase.setText("Delete Sale");
			
			tv_title.setText("Sale Details");
		}
		
		if (transactionType == TYPE_PURCHASE) {

			setTitle("Purchase");

		} else {

			setTitle("Sale");
		}

		// Bulk Import only applies to purchases - a sale has no matching
		// feature here, so the button stays hidden (see the layout's
		// default android:visibility="gone") for TYPE_SALE.
		if (transactionType == TYPE_PURCHASE) {

			btn_bulk_import_purchase.setVisibility(View.VISIBLE);
		}

		transactionId = getIntent().getIntExtra(
			"transaction_id",
			-1
		);
		
		Toast.makeText(
			this,
			"purchaseId=" + transactionId,
			Toast.LENGTH_LONG
		).show();

        if (transactionType == TYPE_PURCHASE) {

			loadTransaction();

		} else {

			loadSale();
		}


		btn_edit_purchase.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					
					Toast.makeText(
						Transactionviewactivity.this,
						"Edit clicked",
						Toast.LENGTH_SHORT
					).show();
					
					
					android.content.Intent intent =
						new android.content.Intent(
						Transactionviewactivity.this,
						Transactioneditactivity.class
					);

					intent.putExtra(
						"transaction_type",
						transactionType
					);

					intent.putExtra(
						"is_edit",
						true
					);

					intent.putExtra(
						"transaction_id",
						transactionId
					);

					startActivity(intent);
					
					Toast.makeText(
						Transactionviewactivity.this,
						"Activity started",
						Toast.LENGTH_SHORT
					).show();
				}
			});

        btn_delete_purchase.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (transactionType == TYPE_PURCHASE) {

						new AlertDialog.Builder(Transactionviewactivity.this)
							.setTitle("Delete Purchase")
							.setMessage("Are you sure you want to delete this purchase?")
							.setPositiveButton(
							"Yes",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {

									if (db.deletePurchase(transactionId)) {

										Toast.makeText(
											Transactionviewactivity.this,
											"Purchase deleted",
											Toast.LENGTH_SHORT
										).show();

										finish();
									}
								}
							})
							.setNegativeButton("No", null)
							.show();

					} else {

						new AlertDialog.Builder(Transactionviewactivity.this)
							.setTitle("Delete Sale")
							.setMessage("Delete this sale?")
							.setPositiveButton(
							"Delete",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {

									db.deleteSaleItems(
										String.valueOf(transactionId)
									);

									db.deleteSale(
										String.valueOf(transactionId)
									);

									Toast.makeText(
										Transactionviewactivity.this,
										"Sale deleted",
										Toast.LENGTH_SHORT
									).show();

									finish();
								}
							})
							.setNegativeButton("Cancel", null)
							.show();
					}
				}
			});

		btn_bulk_import_purchase.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					android.content.Intent intent =
						new android.content.Intent(
						Transactionviewactivity.this,
						BulkPurchaseImportActivity.class
					);

					startActivity(intent);
				}
			});

			}


	private void loadTransaction() {
		if (transactionType == TYPE_SALE) {

			loadSale();
			return;
		}

		HashMap<String, Object> purchase =
			db.getPurchaseById(transactionId);

		if (!purchase.isEmpty()) {

			tv_purchase_code.setVisibility(View.GONE);

			tv_purchase_party.setText(
				"Supplier: " + purchase.get("party_name"));

			tv_purchase_date.setText(
				"Date: " + purchase.get("date")
				+ " "
				+ purchase.get("time"));

			tv_purchase_invoice.setText(
				"Invoice: " + purchase.get("invoice_number"));

			double otherCharges = 0;

			if (purchase.get("other_charges") != null) {
				otherCharges = (Double) purchase.get("other_charges");
			}

			String grandTotalLine =
				"Grand Total: " + AmountFormat.format((Double) purchase.get("grand_total"));

			if (otherCharges != 0) {

				boolean toParty = Boolean.TRUE.equals(purchase.get("other_charges_to_party"));

				grandTotalLine += toParty ?
					" (includes " + AmountFormat.format(otherCharges) + " other charges)" :
					" (other charges of " + AmountFormat.format(otherCharges) +
					" tracked separately, not added to supplier balance)";
			}

			tv_purchase_total.setText(grandTotalLine);

			tv_purchase_paid.setText(
				"Amount Paid: " + AmountFormat.format((Double) purchase.get("amount_paid")));

			tv_purchase_notes.setText(
				"Notes: " + purchase.get("notes"));
		}

		transactionItems = db.getPurchaseItems(transactionId);

		transactionItemAdapter = new TransactionItemAdapter(
			this,
			transactionItems
		);

		lv_transaction_items.setAdapter(transactionItemAdapter);

		setListViewHeightBasedOnChildren(
			lv_transaction_items
		);
	}


	private void setListViewHeightBasedOnChildren(ListView listView) {

		android.widget.ListAdapter listAdapter = listView.getAdapter();

		if (listAdapter == null) {
			return;
		}

		// Measuring with an unconstrained width (the old measure(0, 0))
		// lets a wrapping line - like the "Item: <long name>" line here -
		// measure as if it fit on one line, under-reporting that row's
		// real (taller, wrapped) height. That under-count adds up across
		// every row and the list ends up sized shorter than its real
		// content, clipping the last item(s). Measuring at the list's
		// actual width instead makes each row wrap exactly as it will
		// when shown, so the total height comes out right.
		int listViewWidth = listView.getWidth();

		if (listViewWidth <= 0) {

			// Not laid out yet (this can be the very first call, made
			// from loadTransaction()/loadSale() during onCreate before
			// any layout pass has happened) - fall back to the screen
			// width minus this screen's fixed 16dp side padding, which
			// is what the list will actually end up at.
			android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
			int paddingPx = (int) (32 * metrics.density);
			listViewWidth = metrics.widthPixels - paddingPx;
		}

		int widthSpec = View.MeasureSpec.makeMeasureSpec(
			listViewWidth, View.MeasureSpec.EXACTLY);

		int heightSpec = View.MeasureSpec.makeMeasureSpec(
			0, View.MeasureSpec.UNSPECIFIED);

		int totalHeight = 0;

		for (int i = 0; i < listAdapter.getCount(); i++) {

			View listItem = listAdapter.getView(i, null, listView);

			listItem.measure(widthSpec, heightSpec);

			totalHeight += listItem.getMeasuredHeight();
		}

		android.view.ViewGroup.LayoutParams params =
			listView.getLayoutParams();

		params.height =
			totalHeight +
			(listView.getDividerHeight() *
			(listAdapter.getCount() - 1));

		listView.setLayoutParams(params);

		listView.requestLayout();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (transactionType == TYPE_PURCHASE) {

			loadTransaction();

		} else {

			loadSale();
		}
	}
	
	
	private void loadSale() {

		HashMap<String, Object> sale =
			db.getSaleById(String.valueOf(transactionId));

		if (sale.size() == 0) {
			
			tv_purchase_party.setText("Customer: " + sale.get("party_name"));

			tv_purchase_paid.setText(
				"Paid Amount: " + AmountFormat.format(Double.parseDouble(sale.get("paid_amount").toString()))
			);

			android.widget.Toast.makeText(
				this,
				"Sale not found",
				android.widget.Toast.LENGTH_SHORT
			).show();

			finish();
			return;
		}

		tv_purchase_code.setVisibility(View.GONE);

		tv_purchase_party.setText(
			"Customer: " + sale.get("party_name")
		);

		tv_purchase_date.setText(
			"Date: " +
			sale.get("date") +
			" " +
			sale.get("time")
		);

		tv_purchase_invoice.setText(
			"Invoice: " + sale.get("invoice_no")
		);

		tv_purchase_total.setText(
			"Grand Total: " + AmountFormat.format(Double.parseDouble(sale.get("grand_total").toString()))
		);

		tv_purchase_paid.setText(
			"Amount Paid: " + AmountFormat.format(Double.parseDouble(sale.get("paid_amount").toString()))
		);

		tv_purchase_notes.setText(
			"Notes: " + sale.get("notes")
		);

		transactionItems = db.getSaleItemsForEdit(
			String.valueOf(transactionId)
		);

		transactionItemAdapter =
			new TransactionItemAdapter(
			this,
			transactionItems
		);

		lv_transaction_items.setAdapter(
			transactionItemAdapter
		);

		setListViewHeightBasedOnChildren(
			lv_transaction_items
		);
	}


}
