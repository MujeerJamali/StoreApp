package com.mujeer.businesserp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

// Round-trips a draft's form fields through the drafts table's single
// "data" TEXT column. Drafts are only ever read back by this app itself
// (unlike the Vyapar-interop .vyb format), so plain Java serialization
// Base64-encoded to text is enough - no need to hand-write JSON mapping
// for every field of every draft type.
public class DraftCodec {

	public static String encode(HashMap<String, Object> data) {

		try {

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bytes);
			out.writeObject(data);
			out.close();

			return android.util.Base64.encodeToString(
				bytes.toByteArray(), android.util.Base64.DEFAULT);

		} catch (Exception e) {

			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public static HashMap<String, Object> decode(String encoded) {

		try {

			byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);

			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
			HashMap<String, Object> data = (HashMap<String, Object>) in.readObject();
			in.close();

			return data;

		} catch (Exception e) {

			return new HashMap<String, Object>();
		}
	}
}
