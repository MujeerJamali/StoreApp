package com.mujeer.businesserp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Covers the running-total math the Generate Entries screen relies on to
 * show live totals while tapping the calendar and to compute what gets
 * written to the database on Generate.
 */
public class GenerateEntryLineTest {

	@Test
	public void freshLine_hasZeroTotals() {

		GenerateEntryLine line = new GenerateEntryLine();
		line.unitValue = 100;

		assertEquals(0, line.totalCount());
		assertEquals(0.0, line.totalValue(), 0.0001);
	}

	@Test
	public void totalCount_sumsAcrossAllDates() {

		GenerateEntryLine line = new GenerateEntryLine();
		line.dateCounts.put("2026-01-05", 2);
		line.dateCounts.put("2026-01-12", 3);
		line.dateCounts.put("2026-02-01", 1);

		assertEquals(6, line.totalCount());
	}

	@Test
	public void totalValue_isUnitValueTimesTotalCount() {

		GenerateEntryLine line = new GenerateEntryLine();
		line.unitValue = 50.5;
		line.dateCounts.put("2026-01-05", 2);
		line.dateCounts.put("2026-01-12", 4);

		// (2 + 4) * 50.5
		assertEquals(303.0, line.totalValue(), 0.0001);
	}

	@Test
	public void singleDateSingleTap() {

		GenerateEntryLine line = new GenerateEntryLine();
		line.unitValue = 75;
		line.dateCounts.put("2026-03-10", 1);

		assertEquals(1, line.totalCount());
		assertEquals(75.0, line.totalValue(), 0.0001);
	}

	@Test
	public void repeatedTapsOnSameDate_accumulateIntoOneEntry() {

		// This mirrors how CalendarTapView reports taps: each tap
		// overwrites the same date key with an incremented count, it
		// never adds a second entry for the same date.
		GenerateEntryLine line = new GenerateEntryLine();
		line.unitValue = 10;
		line.dateCounts.put("2026-04-01", 3);

		assertEquals(1, line.dateCounts.size());
		assertEquals(3, line.totalCount());
		assertEquals(30.0, line.totalValue(), 0.0001);
	}
}
