package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// The one physical left-foot sample of each model/size kept out for
// customers to try on - a plain list (unlike Display Shoes' grid,
// since a sample table has no shelf layout to mirror), same data model
// and sale-time removal logic as Display Shoes. See
// DatabaseHelper.getSampleShoes()/DatabaseHelper's Display/Sample
// Shoes section, and Transactioneditactivity's post-sale hook.
// =====================
public class SampleShoesActivity extends Activity {

	private TextView tv_empty;
	private ListView lv_sample_shoes;
	private Button btn_add_sample_shoe;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> sampleList =
		new ArrayList<HashMap<String, Object>>();

	private SampleShoesAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sample_shoes_activity);

		tv_empty = findViewById(R.id.tv_empty);
		lv_sample_shoes = findViewById(R.id.lv_sample_shoes);
		btn_add_sample_shoe = findViewById(R.id.btn_add_sample_shoe);

		lv_sample_shoes.setEmptyView(tv_empty);

		db = new DatabaseHelper(this);

		adapter = new SampleShoesAdapter(this, sampleList, new SampleShoesAdapter.OnRemoveListener() {
				@Override
				public void onRemove(int id) {
					removeSampleShoe(id);
				}
			});

		lv_sample_shoes.setAdapter(adapter);

		btn_add_sample_shoe.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					ShoeBoardPicker.show(
						SampleShoesActivity.this, db, new ShoeBoardPicker.OnPickedListener() {
							@Override
							public void onPicked(int itemId, String itemCode, int comboId, String comboLabel) {
								addSampleShoe(itemId, comboId);
							}
						}
					);
				}
			});

		loadList();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadList();
	}

	private void addSampleShoe(final int itemId, final int comboId) {

		new Thread(new Runnable() {
				@Override
				public void run() {

					db.addSampleShoe(itemId, comboId);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								loadList();
							}
						});
				}
			}).start();
	}

	private void removeSampleShoe(final int id) {

		new Thread(new Runnable() {
				@Override
				public void run() {

					db.removeSampleShoeById(id);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								Toast.makeText(
									SampleShoesActivity.this, "Removed", Toast.LENGTH_SHORT
								).show();

								loadList();
							}
						});
				}
			}).start();
	}

	private void loadList() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getSampleShoes();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (isFinishing()) {
									return;
								}

								sampleList.clear();
								sampleList.addAll(result);

								adapter.notifyDataSetChanged();
							}
						});
				}
			}).start();
	}
}
