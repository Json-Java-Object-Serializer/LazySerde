package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import net.sf.cglib.proxy.Enhancer;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

public class Deserializer {
    private FileInputStream file;
    private Map<Integer, Long> offsets;
    private Map<Integer, String> classNames;
    private Map<Integer, Object> loadedObjects;
    private MyInterceptor interceptor;
    private Objenesis objenesis;
    private Map<Class<?>, ObjectInstantiator<?>> instantiators;
    private List<Integer> primary_ids = new ArrayList<Integer>();
    private int counter = 0;
    private String lastName = "";
    private Integer arrayLength = null;

    public void index(Path filename) throws IOException {
        file = new FileInputStream(String.valueOf(filename));

        getOffsetsWithIds(filename);
        System.out.println(offsets);
        interceptor = new MyInterceptor(this);
        objenesis = new ObjenesisStd();
        instantiators = new HashMap<>();
        loadedObjects = new HashMap<>();
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

        Class<?> actualClazz = Class.forName(classNames.get(id));
        if (!clazz.isAssignableFrom(actualClazz)) {
            throw new Exception("Asked class is wrong!");
        }
        return readObject(id, clazz.isInterface());
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
            return true;
        }

        return false;
    }

    /**
     *
     * @param id internal id of the object
     * @param isLazy whether to omit fields that can be loaded later
     * @return Can return null if loading was not successful
     * @throws Exception
     */
    protected Object readObject(int id, boolean isLazy) throws Exception {
        System.out.println("Reading object with id = " + id);
        if (loadedObjects.containsKey(id)) {
            return loadedObjects.get(id);
        }
        lastName = "";
        arrayLength = 0;
        if (!offsets.containsKey(id) || !classNames.containsKey(id)) {
            return null;
        }
        Class<?> clazz = Class.forName(classNames.get(id));
        Object result;
        if (isLazy) {
            result = Enhancer.create(clazz, interceptor);
        } else {
            if (!instantiators.containsKey(clazz)) {
                instantiators.put(clazz, objenesis.getInstantiatorOf(clazz));
            }
            var instantiator = instantiators.get(clazz);
            try {
                result = instantiator.newInstance();
            } catch (Exception e1) {
                try {
                    result = clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e2) {
                    System.out.println("Failed to initialize object of type: " + clazz.getName());
                    return null;
                }
            }
        }

        loadedObjects.put(id, result);

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
                if (parsePrimitive(jsonParser, field, result)) {
                    continue;
                }

                /*     Parse simple redirection      */
                if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                    int redirection_id = parseRedirection(jsonParser);
                    if (isLazy) {
                        interceptor.setFieldRedirection(result, name, redirection_id);
                    } else {
                        interceptor.setField(result, name, redirection_id);
                    }
                    continue;
                }

                /*    Parse array     */
                if (jsonParser.currentToken() == JsonToken.START_ARRAY) {
                    var type = field.getType();
                    var componentType = type.getComponentType();

                    Object array = null;
                    // TODO: lazy deserialize arrays of primitives
                    if (!isLazy || componentType.isPrimitive() || componentType.equals(String.class)) {
                        array = Array.newInstance(componentType, arrayLength);
                        field.setAccessible(true);
                        field.set(result, array);
                    } else {
                        // Defer array creating until it is actually loaded
                        interceptor.setArraySize(result, name, arrayLength);
                    }

                    int idx = 0;
                    while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                        if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                            int redirection_id = parseRedirection(jsonParser);
                            if (isLazy) {
                                interceptor.setArrayRedirection(result, name, idx, redirection_id);
                            } else {
                                interceptor.setArrayItem(result, name, idx, redirection_id);
                            }
                        } else if (jsonParser.currentToken() != JsonToken.VALUE_NULL) {
                            parseArrayPW(jsonParser, type.getComponentType().toString(), array, idx);
                        }
                        idx++;
                    }

                    field.setAccessible(false);
                }
            }
        }
        return result;
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

    private void parseArrayPW(JsonParser jsonParser, String fieldType, Object array, int idx) throws Exception {
        switch (jsonParser.currentToken()) {
            case VALUE_NUMBER_INT: {
                Array.set(array, idx, jsonParser.getLongValue());
                break;
            }
            case VALUE_NUMBER_FLOAT: {
                Array.set(array, idx, jsonParser.getDoubleValue());
                break;
            }
            case VALUE_TRUE: {
                Array.set(array, idx, true);
                break;
            }
            case VALUE_FALSE: {
                Array.set(array, idx, false);
                break;
            }
            case VALUE_NULL: {
                Array.set(array, idx, null);
                break;
            }
            case VALUE_STRING: {
                if ("char".equals(fieldType)) {
                    Array.set(array, idx, jsonParser.getValueAsString().toCharArray()[0]);
                } else {
                    Array.set(array, idx, jsonParser.getValueAsString());
                }
                break;
            }
            default: {
                throw new IOException();
            }
        }
    }

    public ObjectInterface createProxy(int objectId) throws ClassNotFoundException {
        return new ObjectInterface(objectId);
    }

    public class ObjectInterface {
        private final int objectId;
        private final Class<?> clazz;

        ObjectInterface(int objectId) throws ClassNotFoundException {
            this.objectId = objectId;
            this.clazz = Class.forName(classNames.get(objectId));
        }

        public Object getField(String fieldName) throws Exception {
            JsonFactory jsonFactory = new JsonFactory();
            file.getChannel().position(offsets.get(objectId));
            JsonParser jsonParser = jsonFactory.createParser(file);

            // Skip START_OBJECT token
            jsonParser.nextToken();
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.currentToken() == JsonToken.FIELD_NAME) {
                    String name = jsonParser.currentName();
                    if (name.equals(fieldName)) {
                        break;
                    } else if (name.endsWith("length")) {
                        jsonParser.nextToken();
                        arrayLength = jsonParser.getIntValue();
                    }
                } else if (jsonParser.currentToken() == JsonToken.START_OBJECT || jsonParser.currentToken() == JsonToken.START_ARRAY) {
                    jsonParser.skipChildren();
                }
            }

            if (jsonParser.currentToken() == JsonToken.END_OBJECT) {
                System.out.println("Failed to read field " + fieldName + " on object with id = " + objectId);
                return null;
            }

            // Go to the value of the field
            jsonParser.nextToken();
            return switch (jsonParser.currentToken()) {
                case VALUE_NUMBER_INT -> jsonParser.getLongValue();
                case VALUE_NUMBER_FLOAT -> jsonParser.getDoubleValue();
                case VALUE_FALSE -> false;
                case VALUE_TRUE -> true;
                case VALUE_NULL -> null;
                case VALUE_STRING -> jsonParser.getValueAsString();
                case START_OBJECT -> {
                    var targetId = parseRedirection(jsonParser);
                    yield readObject(targetId, false);
                }
                case START_ARRAY -> {
                    var field = clazz.getDeclaredField(fieldName);
                    var type = field.getType();
                    var componentType = type.getComponentType();

                    Object array = Array.newInstance(componentType, arrayLength);

                    int idx = 0;
                    while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                        if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
                            int redirection_id = parseRedirection(jsonParser);
                            Array.set(array, idx, readObject(redirection_id, false));
                        } else if (jsonParser.currentToken() != JsonToken.VALUE_NULL) {
                            parseArrayPW(jsonParser, type.getComponentType().toString(), array, idx);
                        }
                        idx++;
                    }
                    yield array;
                }
                case NOT_AVAILABLE, END_OBJECT, FIELD_NAME, END_ARRAY, VALUE_EMBEDDED_OBJECT -> throw new Exception("Parsing error");
            };
        }
    }
}
