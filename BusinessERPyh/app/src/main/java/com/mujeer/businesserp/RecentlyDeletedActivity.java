package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every deleted OR edited Purchase/Sale still in the trash - undo-
// beyond-delete: alongside the original delete snapshots (see
// DatabaseHelper.snapshotAndDeletePurchase()/snapshotAndDeleteSale(),
// the wrappers Transactionactivity calls instead of deleting directly),
// Transactioneditactivity also snapshots the pre-edit row/items right
// before an Update applies (snapshotSaleBeforeEdit()/
// snapshotPurchaseBeforeEdit()), so an accidental edit is just as
// undoable as a delete. Tapping a row restores/undoes it exactly as it
// was, including re-applying the same balance effects a fresh save
// would have made; long-pressing a row purges it immediately instead
// of waiting for the 30-day auto-purge (BusinessERPApplication.
// onCreate() -> DatabaseHelper.purgeOldRecentlyDeleted()).
// =====================
public class RecentlyDeletedActivity extends Activity {

	private TextView tv_empty;
	private ListView lv_recently_deleted;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> trashList =
		new ArrayList<HashMap<String, Object>>();

	private RecentlyDeletedAdapter adapter;

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recently_deleted_activity);

		setTitle("Recently Deleted");

		tv_empty = findViewById(R.id.tv_empty);
		lv_recently_deleted = findViewById(R.id.lv_recently_deleted);

		db = new DatabaseHelper(this);

		adapter = new RecentlyDeletedAdapter(this, trashList);
		lv_recently_deleted.setAdapter(adapter);

		lv_recently_deleted.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					final HashMap<String, Object> row = trashList.get(position);
					final int trashId = (Integer) row.get("id");
					final String type = (String) row.get("type");
					final boolean isPurchase = "purchase".equals(type) || "purchase_edit".equals(type);
					final boolean isEdit = "sale_edit".equals(type) || "purchase_edit".equals(type);

					new AlertDialog.Builder(RecentlyDeletedActivity.this)
						.setTitle(
							isEdit
							? "Undo this edit?"
							: "Restore this " + (isPurchase ? "purchase" : "sale") + "?"
						)
						.setMessage(
							isEdit
							? "Reverts \"" + row.get("label") + "\" back to how it was before this edit, " +
								"re-applying its earlier stock/balance effects."
							: "Brings back \"" + row.get("label") + "\" and re-applies its " +
								"original stock/balance effects."
						)
						.setPositiveButton(isEdit ? "Undo" : "Restore", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {

									boolean restored =
										isEdit
										? (isPurchase ?
											db.undoPurchaseEdit(trashId) : db.undoSaleEdit(trashId))
										: (isPurchase ?
											db.restorePurchaseFromTrash(trashId) : db.restoreSaleFromTrash(trashId));

									Toast.makeText(
										RecentlyDeletedActivity.this,
										restored
											? (isEdit ? "Edit undone" : "Restored")
											: "Could not " + (isEdit ? "undo" : "restore") +
												" - it may already be gone.",
										Toast.LENGTH_SHORT
									).show();

									loadTrash();
								}
							})
						.setNegativeButton("Cancel", null)
						.show();
				}
			});

		lv_recently_deleted.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

					final HashMap<String, Object> row = trashList.get(position);
					final int trashId = (Integer) row.get("id");

					new AlertDialog.Builder(RecentlyDeletedActivity.this)
						.setTitle("Permanently delete?")
						.setMessage("\"" + row.get("label") + "\" cannot be restored after this.")
						.setPositiveButton("Delete Forever", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {

									db.deleteRecentlyDeletedPermanently(trashId);
									loadTrash();
								}
							})
						.setNegativeButton("Cancel", null)
						.show();

					return true;
				}
			});

		loadTrash();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadTrash();
	}

	private void loadTrash() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getRecentlyDeleted();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								trashList.clear();
								trashList.addAll(result);

								adapter.notifyDataSetChanged();

								if (trashList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_recently_deleted.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_recently_deleted.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
