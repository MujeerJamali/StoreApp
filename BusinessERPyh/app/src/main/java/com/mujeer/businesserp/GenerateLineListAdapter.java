package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a list of GenerateEntryLine rows - used for both the
 * Payment/Expense "lines so far" list and the final review list. Each row
 * shows the line's label, a short per-date breakdown, and its total value.
 */
public class GenerateLineListAdapter extends BaseAdapter {

	private final Activity activity;
	private final List<GenerateEntryLine> lines;
	private final boolean showUnitValue;

	public GenerateLineListAdapter(
		Activity activity,
		List<GenerateEntryLine> lines,
		boolean showUnitValue) {

		this.activity = activity;
		this.lines = lines;
		this.showUnitValue = showUnitValue;
	}

	@Override
	public int getCount() {
		return lines.size();
	}

	@Override
	public Object getItem(int position) {
		return lines.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {
			convertView = LayoutInflater.from(activity)
				.inflate(R.layout.row_generate_line_summary, parent, false);
		}

		TextView tvLabel = convertView.findViewById(R.id.tv_line_label);
		TextView tvDetail = convertView.findViewById(R.id.tv_line_detail);
		TextView tvTotal = convertView.findViewById(R.id.tv_line_total);

		GenerateEntryLine line = lines.get(position);

		boolean configured = line.label != null && line.label.trim().length() > 0;

		tvLabel.setText(configured ? line.label : "(tap to configure)");
		tvLabel.setTextColor(activity.getResources().getColor(
			configured ? R.color.text_primary : R.color.text_hint));
		tvDetail.setText(formatDetail(line));
		tvTotal.setText(AmountFormat.format(line.totalValue()));

		return convertView;
	}

	private String formatDetail(GenerateEntryLine line) {

		int dateCount = line.dateCounts.size();

		if (dateCount == 0) {
			return "No dates selected yet";
		}

		StringBuilder sb = new StringBuilder();

		if (dateCount <= 5) {

			List<String> keys = new ArrayList<>(line.dateCounts.keySet());
			java.util.Collections.sort(keys);

			for (int i = 0; i < keys.size(); i++) {

				if (i > 0) {
					sb.append("  ·  ");
				}

				sb.append(formatShortDate(keys.get(i)));
				sb.append(": ");
				sb.append(line.dateCounts.get(keys.get(i)));
			}

		} else {

			sb.append(dateCount).append(" dates, ").append(line.totalCount()).append(" total");
		}

		if (showUnitValue) {
			sb.append("  ·  @ ").append(AmountFormat.format(line.unitValue));
		}

		return sb.toString();
	}

	private String formatShortDate(String isoDate) {

		try {

			return new SimpleDateFormat("dd MMM", Locale.US).format(
				new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate));

		} catch (Exception e) {

			return isoDate;
		}
	}
}
