package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Deserializer {
    private FileInputStream file;
    private Map<Integer, Long> offsets;
    private List<Integer> primary_ids = new ArrayList<Integer>();

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
                    if (jsonParser.currentToken() == JsonToken.START_OBJECT || jsonParser.currentToken() == JsonToken.START_ARRAY) {
                        jsonParser.skipChildren();
                    } else if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@id".equals(jsonParser.currentName())) {
                        jsonParser.nextToken(); // Move to the value
                        id = jsonParser.getIntValue();
                    } else if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@primary".equals(jsonParser.currentName())) {
                        jsonParser.nextToken();
                        if (jsonParser.getBooleanValue()) {
                            // ID have to go before primary!!!
                            primary_ids.add(id);
                        }
                    }
                }

                if (id != -1) {
                    offsets.put(id, offset);
                }
            }
        }
    }

    // returns next primary object
    private int counter = 0;
    public Object getNext(Class<?> clazz) throws Exception {
        if (counter >= primary_ids.size()) {
            return null;
        }
        return readObject(clazz, primary_ids.get(counter++));
    }

    private Object readObject(Class<?> clazz, int id) throws Exception {
        if (!offsets.containsKey(id)) {
            return null;
        }

        Object result = clazz.newInstance();

        // TODO: create proxy for class
        JsonFactory jsonFactory = new JsonFactory();
        file.getChannel().position(offsets.get(id));
        JsonParser jsonParser = jsonFactory.createParser(file);

        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            if (jsonParser.currentToken() == JsonToken.FIELD_NAME) {

                String name = jsonParser.currentName();
                if (name.startsWith("@")) {
                    jsonParser.nextToken();
                    continue;
                }
                var field = clazz.getDeclaredField(name);

                jsonParser.nextToken();
                parseField(jsonParser, field, result);
                if (jsonParser.currentToken() == JsonToken.START_ARRAY || jsonParser.currentToken() == JsonToken.START_OBJECT) {
                    jsonParser.skipChildren();
                }
            }
        }

        return result;
    }



    private void parseField(JsonParser jsonParser, Field field, Object result) throws Exception {
        String fieldType = field.getType().toString();
        field.setAccessible(true);

        switch (jsonParser.currentToken()) {
            case VALUE_NUMBER_INT: {
                if ("int".equals(fieldType)) {
                    field.setInt(result, jsonParser.getIntValue());
                } else if ("long".equals(fieldType)) {
                    field.setLong(result, jsonParser.getIntValue());
                } else if ("short".equals(fieldType)){
                    field.setShort(result, jsonParser.getShortValue());
                } else if ("byte".equals(fieldType)) {
                    field.setByte(result, jsonParser.getByteValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NUMBER_FLOAT: {
                if ("float".equals(fieldType)) {
                    field.setFloat(result, jsonParser.getFloatValue());
                } else if ("double".equals(fieldType)) {
                    field.setDouble(result, jsonParser.getDoubleValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_TRUE: {
                if ("boolean".equals(fieldType)) {
                    field.setBoolean(result, true);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_FALSE: {
                if ("boolean".equals(fieldType)) {
                    field.setBoolean(result, false);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NULL: {
                field.set(result, null);
                break;
            }
            case VALUE_STRING: {
                if ("class java.lang.String".equals(fieldType)) {
                    field.set(result, jsonParser.getValueAsString());
                } else if ("char".equals(fieldType)){
                    field.set(result, jsonParser.getValueAsString().toCharArray()[0]);
                } else {
                    throw new Exception("Unknown field type");
                }
                break;
            }
            case START_OBJECT: {
                // "@NESTED OBJECT DETECTED";
                break;
            }
            case START_ARRAY: {
                //"@ARRAY DETECTED";

                break;
            }
            case END_ARRAY: {
                // throw new IOException();
                break;
            }


            case NOT_AVAILABLE: {
                throw new IOException();
            }
            case END_OBJECT, FIELD_NAME: {
                throw new IOException();
            }
            case VALUE_EMBEDDED_OBJECT: {
                throw new IOException();
            }
        }

        field.setAccessible(false);
    }

}
