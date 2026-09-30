package com.mujeer.businesserp;

// Parses this shop's shoe item naming convention - verified 100%
// consistent across every real shoe item in the catalog, zero
// exceptions found:
//
//   "Shoes {Gender} {Type} {Sole} {Upper} {Design} {Color} {Size} - {Code}"
//
// Used wherever a shoes-only report needs to dedupe items that are the
// same physical model down to size (e.g. the same shoe re-listed once
// per purchase batch, each with its own item code) - the "drops just
// code" rule: the identity key is everything except the trailing
// " - {Code}", so Size is KEPT (two entries differing only by size
// range stay separate), only the code is dropped.
public class ShoeIdentity {

	public final String gender;
	public final String type;
	public final String sole;
	public final String upper;
	public final String design;
	public final String color;
	public final String size;
	public final String code;

	// Everything but the code - the dedup/grouping key.
	public final String identityKey;

	private ShoeIdentity(
		String gender, String type, String sole, String upper, String design,
		String color, String size, String code, String identityKey) {

		this.gender = gender;
		this.type = type;
		this.sole = sole;
		this.upper = upper;
		this.design = design;
		this.color = color;
		this.size = size;
		this.code = code;
		this.identityKey = identityKey;
	}

	// Cheap check for "is this even a shoe item" - doesn't require the
	// full 8-token shape to match, just the leading marker. Use this (not
	// parse() != null) for filters that only need shoe/non-shoe, not the
	// individual fields.
	public static boolean isShoe(String itemName) {
		return itemName != null && itemName.trim().startsWith("Shoes ");
	}

	// Returns null if itemName doesn't match the expected shape (e.g. a
	// non-shoe item, or a shoe item that predates/breaks the convention) -
	// callers should fall back to treating it as unparseable rather than
	// guessing.
	public static ShoeIdentity parse(String itemName) {

		if (itemName == null) {
			return null;
		}

		String name = itemName.trim();
		int dashIndex = name.lastIndexOf(" - ");

		if (dashIndex < 0) {
			return null;
		}

		String identityKey = name.substring(0, dashIndex).trim();
		String code = name.substring(dashIndex + 3).trim();

		String[] tokens = identityKey.split("\\s+");

		if (tokens.length != 8 || !"Shoes".equals(tokens[0])) {
			return null;
		}

		return new ShoeIdentity(
			tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6], tokens[7],
			code, identityKey
		);
	}
}
