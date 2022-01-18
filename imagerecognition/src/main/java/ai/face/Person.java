package ai.face;

import java.util.UUID;

/**
 * A class representing an arbitrary person.
 * */
public class Person {
	public final UUID id;
	public final FaceFeature feature;

	public Person(UUID id, FaceFeature feature) {
		this.id = id;
		this.feature = feature;
	}
}
