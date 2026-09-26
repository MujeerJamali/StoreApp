package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// A Sale, Purchase, Payment or Expense saved mid-entry (see each
// editor's "Save as Draft" button) instead of committed for real.
// Tapping one opens the right editor prefilled from it; Delete discards
// it outright. See DatabaseHelper's drafts table methods and
// DraftCodec for how a draft's fields are stored/restored.
// =====================
public class DraftsActivity extends Activity {

	private ListView lvDrafts;
	private TextView tvEmpty;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> draftList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.drafts_activity);

		setTitle("Drafts");

		lvDrafts = findViewById(R.id.lv_drafts);
		tvEmpty = findViewById(R.id.tv_drafts_empty);

		db = new DatabaseHelper(this);

		draftList = new ArrayList<HashMap<String, Object>>();

		lvDrafts.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent, android.view.View view, int position, long id) {

					openDraft(draftList.get(position));
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadDrafts();
	}

	private void loadDrafts() {

		draftList.clear();
		draftList.addAll(db.getAllDrafts());

		DraftsAdapter adapter = new DraftsAdapter(
			this,
			draftList,
			new DraftsAdapter.OnDeleteClickListener() {

				@Override
				public void onDeleteClick(int draftId) {
					confirmDeleteDraft(draftId);
				}
			}
		);

		lvDrafts.setAdapter(adapter);
		lvDrafts.setEmptyView(tvEmpty);
	}

	private void confirmDeleteDraft(final int draftId) {

		new AlertDialog.Builder(this)
			.setTitle("Delete Draft")
			.setMessage("Delete this draft? This cannot be undone.")
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {

						db.deleteDraft(draftId);
						loadDrafts();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void openDraft(HashMap<String, Object> draft) {

		int draftId = (Integer) draft.get("id");
		String type = (String) draft.get("type");

		Intent intent;

		if (DatabaseHelper.DRAFT_TYPE_PURCHASE.equals(type)) {

			intent = new Intent(this, Transactioneditactivity.class);
			intent.putExtra("transaction_type", 0); // TYPE_PURCHASE
			intent.putExtra("draft_id", draftId);

		} else if (DatabaseHelper.DRAFT_TYPE_SALE.equals(type)) {

			intent = new Intent(this, Transactioneditactivity.class);
			intent.putExtra("transaction_type", 1); // TYPE_SALE
			intent.putExtra("draft_id", draftId);

		} else if (DatabaseHelper.DRAFT_TYPE_PAYMENT.equals(type)) {

			intent = new Intent(this, Paymenteditactivity.class);
			intent.putExtra("draft_id", draftId);

		} else if (DatabaseHelper.DRAFT_TYPE_EXPENSE.equals(type)) {

			intent = new Intent(this, Expenseeditactivity.class);
			intent.putExtra("draft_id", draftId);

		} else {

			return;
		}

		startActivity(intent);
	}
}
