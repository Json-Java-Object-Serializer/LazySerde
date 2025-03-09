package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import net.sf.cglib.proxy.Enhancer;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Deserializer {
    private FileInputStream file;
    private Map<Integer, Long> offsets;
    private Map<Integer, String> classNames;
    private List<Integer> primary_ids = new ArrayList<Integer>();
    private int counter = 0;
    private String lastName = "";
    private Integer arrayLength = null;
    private String arrayType = null;

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
        classNames = new HashMap<>();

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
                String className = "";

                // Traverse the object to find the "@id" field
                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    if (jsonParser.currentToken() == JsonToken.START_OBJECT || jsonParser.currentToken() == JsonToken.START_ARRAY) {
                        jsonParser.skipChildren();
                    } else if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@className".equals(jsonParser.currentName())) {
                        jsonParser.nextToken(); // Move to the value
                        className = jsonParser.getValueAsString();
                    }
                    if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@id".equals(jsonParser.currentName())) {
                        jsonParser.nextToken(); // Move to the value
                        id = jsonParser.getIntValue();
                    } else if (jsonParser.currentToken() == JsonToken.FIELD_NAME && "@primary".equals(jsonParser.currentName())) {
                        jsonParser.nextToken();
                        if (jsonParser.getBooleanValue()) {
                            // ID have to go before primary!!!
                            assert (id != -1);
                            primary_ids.add(id);
                        }
                    }
                }

                if (id != -1) {
                    offsets.put(id, offset);
                    classNames.put(id, className);
                }
            }
        }
    }

    public Object getNext(Class<?> clazz) throws Exception {
        if (counter >= primary_ids.size()) {
            return null;
        }
        int id = primary_ids.get(counter);
        counter++;

        if (!offsets.containsKey(id)) {
            return null;
        }
        if (!clazz.getName().equals(classNames.get(id))) {
            throw new Exception("Asked class is wrong!");
        }
        return readObject(id);
    }

    //     Fills metafields and returns true if have to skip this row of JSON.
    private boolean processMetaFields(JsonParser jsonParser, String name) throws IOException {
        if (name.startsWith("@")) {
            jsonParser.nextToken();
            return true;
        }
        if (name.endsWith("@length")) {
            if (!name.equals(lastName + "@length")) {
                lastName = name.split("@")[0];
                arrayType = null;
            }
            jsonParser.nextToken();
            arrayLength = jsonParser.getIntValue();
            return true;
        }
        if (name.endsWith("@type")) {
            if (!name.equals(lastName + "@type")) {
                lastName = name.split("@")[0];
                arrayLength = null;
            }
            jsonParser.nextToken();
            arrayType = jsonParser.getValueAsString();
            return true;
        }

        return false;
    }

    protected Object readObject(int id) throws Exception {
        lastName = "";
        arrayLength = 0;
        arrayType = "";
        if (!offsets.containsKey(id) || !classNames.containsKey(id)) {
            return null;
        }
        Class<?> clazz = Class.forName(classNames.get(id));
        // TODO: think about not default constructors. Maybe just don't allow them by annotations
        MyInterceptor interceptor = new MyInterceptor(this);
        Object result_proxy = Enhancer.create(
                clazz,
                interceptor
        );

        JsonFactory jsonFactory = new JsonFactory();
        file.getChannel().position(offsets.get(id));
        JsonParser jsonParser = jsonFactory.createParser(file);

        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            if (jsonParser.currentToken() == JsonToken.FIELD_NAME) {
                String name = jsonParser.currentName();

                if (processMetaFields(jsonParser, name)) {
                    continue;
                }

                var field = clazz.getDeclaredField(name);
                jsonParser.nextToken();
                if (parsePrimitive(jsonParser, field, result_proxy)) {
                    continue;
                }

                /*     Parse simple redirection      */
                if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                    int redirection_id = parseRedirection(jsonParser);
                    interceptor.setFieldRedirection(name, redirection_id);
                    continue;
                }

                /*    Parse array: primitive and redirections     */
                if (jsonParser.currentToken() == JsonToken.START_ARRAY) {
                    int idx = 0;
                    boolean is_wrapper = false;
                    boolean is_primitive = false;

                    var type = field.getType();
                    Object array = null;
                    if (lastName.equals(name) && arrayType != null) {
                        // Case when array type is primitive
                        is_wrapper = true;
                        array = Array.newInstance(type.getComponentType(), arrayLength);
                        field.setAccessible(true);
                        field.set(result_proxy, array);
                    }
                    if (!is_wrapper && type.getComponentType().isPrimitive() || type.getComponentType().equals(String.class)) {
                        // Case when array type is primitive
                        is_primitive = true;
                        array = Array.newInstance(type.getComponentType(), arrayLength);
                        field.setAccessible(true);
                        field.set(result_proxy, array);
                    }

                    while (true) {
                        jsonParser.nextToken();
                        if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                            if (is_primitive) {
                                throw new Exception();
                            }
                            int redirection_id = parseRedirection(jsonParser);
                            interceptor.setArrayRedirection(name, idx, redirection_id);
                        } else if (jsonParser.currentToken() == JsonToken.END_ARRAY) {
                            break;
                        } else {
                            if (is_primitive) {
                                parseArrayPrimitive(jsonParser, type.getComponentType().toString(), array, idx);
                            } else if (is_wrapper) {
                                parseArrayWrapper(jsonParser, arrayType, array, idx);
                            } else {
                                throw new Exception();
                            }

                        }
                        idx++;
                    }

                    if (is_primitive) {
                        field.setAccessible(false);
                    }
                }
            }
        }

        return result_proxy;
    }

    private int parseRedirection(JsonParser jsonParser) throws Exception {
        jsonParser.nextToken();
        if (jsonParser.currentToken() != JsonToken.FIELD_NAME || !jsonParser.currentName().equals("@redirect")) {
            throw new Exception("Bad redirection format");
        }

        jsonParser.nextToken();
        if (jsonParser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new Exception("Bad redirection format");
        }
        int id = jsonParser.getIntValue();
        jsonParser.nextToken();
        if (jsonParser.currentToken() != JsonToken.END_OBJECT) {
            throw new Exception("Bad redirection format");
        }
        return id;
    }

    private boolean parsePrimitive(JsonParser jsonParser, Field field, Object result) throws Exception {
        String fieldType = field.getType().toString();
        field.setAccessible(true);


        switch (jsonParser.currentToken()) {
            case VALUE_NUMBER_INT: {
                if ("int".equals(fieldType)) {
                    field.setInt(result, jsonParser.getIntValue());
                } else if ("long".equals(fieldType)) {
                    field.setLong(result, jsonParser.getLongValue());
                } else if ("short".equals(fieldType)) {
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
                } else if ("char".equals(fieldType)) {
                    field.set(result, jsonParser.getValueAsString().toCharArray()[0]);
                } else {
                    throw new Exception("Unknown field type");
                }
                break;
            }
            case NOT_AVAILABLE, END_OBJECT, END_ARRAY, FIELD_NAME, VALUE_EMBEDDED_OBJECT: {
                field.setAccessible(false);
                throw new IOException();
            }
            default: {
                field.setAccessible(false);
                return false;
            }
        }

        field.setAccessible(false);
        return true;
    }

    private void parseArrayWrapper(JsonParser jsonParser, String fieldType, Object array, int idx) throws Exception {
        switch (jsonParser.currentToken()) {
            case VALUE_NUMBER_INT: {
                if ("java.lang.Integer".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getIntValue());
                } else if ("java.lang.Long".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getLongValue());
                } else if ("java.lang.Short".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getShortValue());
                } else if ("java.lang.Byte".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getByteValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NUMBER_FLOAT: {
                if ("java.lang.Float".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getFloatValue());
                } else if ("java.lang.Double".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getDoubleValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_TRUE: {
                if ("java.lang.Boolean".equals(fieldType)) {
                    Array.set(array, idx, true);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_FALSE: {
                if ("java.lang.Boolean".equals(fieldType)) {
                    Array.set(array, idx, false);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NULL: {
                Array.set(array, idx, null);
                break;
            }
            case VALUE_STRING: {
                if ("java.lang.String".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getValueAsString());
                } else if ("java.lang.Character".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getValueAsString().toCharArray()[0]);
                } else {
                    throw new Exception("Unknown field type");
                }
                break;
            }
            default: {
                throw new IOException();
            }
        }
    }

    private void parseArrayPrimitive(JsonParser jsonParser, String fieldType, Object array, int idx) throws Exception {
        switch (jsonParser.currentToken()) {
            case VALUE_NUMBER_INT: {
                if ("int".equals(fieldType)) {
                    Array.setInt(array, idx, jsonParser.getIntValue());
                } else if ("long".equals(fieldType)) {
                    Array.setLong(array, idx, jsonParser.getLongValue());
                } else if ("short".equals(fieldType)) {
                    Array.setShort(array, idx, jsonParser.getShortValue());
                } else if ("byte".equals(fieldType)) {
                    Array.setByte(array, idx, jsonParser.getByteValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NUMBER_FLOAT: {
                if ("float".equals(fieldType)) {
                    Array.setFloat(array, idx, jsonParser.getFloatValue());
                } else if ("double".equals(fieldType)) {
                    Array.setDouble(array, idx, jsonParser.getDoubleValue());
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_TRUE: {
                if ("boolean".equals(fieldType)) {
                    Array.setBoolean(array, idx, true);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_FALSE: {
                if ("boolean".equals(fieldType)) {
                    Array.setBoolean(array, idx, false);
                } else {
                    throw new Exception("Unknown field Type");
                }
                break;
            }
            case VALUE_NULL: {
                Array.set(array, idx, null);
                break;
            }
            case VALUE_STRING: {
                if ("class java.lang.String".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getValueAsString());
                } else if ("char".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getValueAsString().toCharArray()[0]);
                } else {
                    throw new Exception("Unknown field type");
                }
                break;
            }
            default: {
                throw new IOException();
            }
        }
    }

}
