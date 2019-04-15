package org.jboss.pme.alignment;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class SerializationUtils {

	private SerializationUtils() {
	}

	private static ObjectMapper mapper;

	public static ObjectMapper getObjectMapper() {
		if (mapper == null) {
			mapper = new ObjectMapper();
		}
		return mapper;
	}
}
