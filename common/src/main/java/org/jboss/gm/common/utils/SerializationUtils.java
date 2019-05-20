package org.jboss.gm.common.utils;

import java.io.IOException;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.jboss.gm.common.ProjectVersionFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public final class SerializationUtils {

    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";

    private SerializationUtils() {
    }

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ProjectVersionRef.class, new ProjectVersionRefDeserializer());
        module.addSerializer(ProjectVersionRef.class, new ProjectVersionRefSerializer());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.registerModule(module);
    }

    public static ObjectMapper getObjectMapper() {
        return mapper;
    }

    private static class ProjectVersionRefDeserializer extends JsonDeserializer<ProjectVersionRef> {

        @Override
        public ProjectVersionRef deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            final String groupId = node.get(GROUP_ID).asText();
            final String artifactId = node.get(ARTIFACT_ID).asText();
            final String version = node.get(VERSION).asText();

            return ProjectVersionFactory.withGAV(groupId, artifactId, version);
        }
    }

    private static class ProjectVersionRefSerializer extends JsonSerializer<ProjectVersionRef> {

        @Override
        public void serialize(ProjectVersionRef value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField(GROUP_ID, value.getGroupId());
            gen.writeStringField(ARTIFACT_ID, value.getArtifactId());
            gen.writeStringField(VERSION, value.getVersionString());
            gen.writeEndObject();
        }
    }
}
