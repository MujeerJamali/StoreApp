package com.mujeer.businesserp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

// A small dependency-free bar chart for comparing a handful of labeled
// values (e.g. Sales/Item Cost/Expenses/Net Profit on one report) -
// deliberately not a trend-over-time chart, and not backed by a
// third-party library: this project has no charting dependency wired
// into build.gradle, and AIDE's on-device build has no reliable way to
// resolve a new Maven dependency, so every chart in the app draws
// itself with plain Canvas calls instead. Handles negative values
// (e.g. a loss) by drawing them below a shared zero baseline.
public class SimpleBarChartView extends View {

	public static class Entry {

		public final String label;
		public final double value;
		public final int color;

		public Entry(String label, double value, int color) {
			this.label = label;
			this.value = value;
			this.color = color;
		}
	}

	private List<Entry> entries = new ArrayList<Entry>();

	private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public SimpleBarChartView(Context context) {
		super(context);
		init();
	}

	public SimpleBarChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {

		float density = getResources().getDisplayMetrics().density;

		labelPaint.setColor(Color.parseColor("#6B7280"));
		labelPaint.setTextSize(11 * density);
		labelPaint.setTextAlign(Paint.Align.CENTER);

		valuePaint.setColor(Color.parseColor("#1F2937"));
		valuePaint.setTextSize(11 * density);
		valuePaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setFakeBoldText(true);

		baselinePaint.setColor(Color.parseColor("#EAECF3"));
		baselinePaint.setStrokeWidth(1 * density);
	}

	public void setEntries(List<Entry> entries) {
		this.entries = entries == null ? new ArrayList<Entry>() : entries;
		requestLayout();
		invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		float density = getResources().getDisplayMetrics().density;
		int desiredHeight = (int) (150 * density);

		int width = MeasureSpec.getSize(widthMeasureSpec);

		setMeasuredDimension(width, desiredHeight);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (entries.isEmpty()) {
			return;
		}

		float density = getResources().getDisplayMetrics().density;

		int width = getWidth();
		int height = getHeight();

		float labelHeight = 16 * density;
		float valueHeight = 16 * density;
		float topPadding = 8 * density;
		float bottomPadding = 4 * density;

		float plotTop = topPadding + valueHeight;
		float plotBottom = height - bottomPadding - labelHeight;
		float plotHeight = plotBottom - plotTop;

		double maxAbs = 0.0001;

		for (Entry e : entries) {
			maxAbs = Math.max(maxAbs, Math.abs(e.value));
		}

		boolean hasNegative = false;

		for (Entry e : entries) {
			if (e.value < 0) {
				hasNegative = true;
			}
		}

		// With no negative values, bars grow up from the bottom (more
		// screen space for the bars); with a mix, zero sits in the
		// middle of the plot area so a loss can grow downward.
		float baselineY = hasNegative ? plotTop + plotHeight / 2f : plotBottom;
		float positiveSpan = hasNegative ? plotHeight / 2f : plotHeight;
		float negativeSpan = plotHeight / 2f;

		canvas.drawLine(0, baselineY, width, baselineY, baselinePaint);

		int count = entries.size();
		float slotWidth = width / (float) count;
		float barWidth = slotWidth * 0.5f;

		for (int i = 0; i < count; i++) {

			Entry e = entries.get(i);

			float centerX = slotWidth * i + slotWidth / 2f;

			float barHeight = (float) (Math.abs(e.value) / maxAbs *
				(e.value >= 0 ? positiveSpan : negativeSpan));

			barPaint.setColor(e.color);

			float top;
			float bottom;

			if (e.value >= 0) {
				top = baselineY - barHeight;
				bottom = baselineY;
			} else {
				top = baselineY;
				bottom = baselineY + barHeight;
			}

			canvas.drawRect(centerX - barWidth / 2f, top, centerX + barWidth / 2f, bottom, barPaint);

			float valueY = e.value >= 0 ? Math.max(top - 4 * density, topPadding + valueHeight - 2 * density) : bottom + valueHeight - 2 * density;

			canvas.drawText(AmountFormat.format(e.value), centerX, valueY, valuePaint);

			canvas.drawText(e.label, centerX, height - 2 * density, labelPaint);
		}
	}
}
