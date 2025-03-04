package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Deserializer {
    private FileInputStream file;
    private Map<Integer, Long> offsets;

    public void index(Path filename) throws IOException {
        file = new FileInputStream(String.valueOf(filename));

        getOffsetsWithIds(filename);
        System.out.println(offsets);
    }

    private void getOffsetsWithIds(Path filename) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        file.getChannel().position(0);
        JsonParser jsonParser = jsonFactory.createParser(file);

        offsets = new HashMap<>();

        while (jsonParser.nextToken() != null) {
            if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "serialization".equals(jsonParser.currentName())) {
                break;
            }
        }

        if (jsonParser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Field 'serialization' is not an array.");
        }

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                long offset = jsonParser.currentTokenLocation().getByteOffset();
                int id = -1;

                // Traverse the object to find the "@id" field
                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    if (jsonParser.currentToken() == JsonToken.START_ARRAY || jsonParser.currentToken() == JsonToken.START_ARRAY) {
                        jsonParser.skipChildren();
                    } else if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@id".equals(jsonParser.currentName())) {
                        jsonParser.nextToken(); // Move to the value
                        id = jsonParser.getIntValue();
                    }
                }

                if (id != -1) {
                    offsets.put(id, offset);
                }
            }
        }
    }

    public HashMap<String, Object> readObject(int id) throws IOException {
        if (!offsets.containsKey(id)) {
            return null;
        }

        HashMap<String, Object> result = new HashMap<>();

        JsonFactory jsonFactory = new JsonFactory();
        file.getChannel().position(offsets.get(id));
        JsonParser jsonParser = jsonFactory.createParser(file);
        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            if (jsonParser.currentToken() == JsonToken.FIELD_NAME) {
                String name = jsonParser.currentName();
                jsonParser.nextToken();
                var value = switch (jsonParser.currentToken()) {
                    case VALUE_NUMBER_INT -> jsonParser.getIntValue();
                    case VALUE_NUMBER_FLOAT -> jsonParser.getFloatValue();
                    case VALUE_TRUE -> true;
                    case VALUE_FALSE -> false;
                    case VALUE_NULL -> null;
                    case VALUE_STRING -> jsonParser.getValueAsString();
                    case NOT_AVAILABLE -> throw new IOException();
                    case START_OBJECT -> "@NESTED OBJECT DETECTED";
                    case END_OBJECT -> throw new IOException();
                    case START_ARRAY -> "@ARRAY DETECTED";
                    case END_ARRAY -> throw new IOException();
                    case FIELD_NAME -> throw new IOException();
                    case VALUE_EMBEDDED_OBJECT -> throw new IOException();
                };
                if (jsonParser.currentToken() == JsonToken.START_ARRAY || jsonParser.currentToken() == JsonToken.START_OBJECT) {
                    jsonParser.skipChildren();
                }
                result.put(name, value);
            }
        }
        return result;
    }
}
