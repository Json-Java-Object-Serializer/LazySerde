package lazySerde;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;

public class Serializer {
    private final ArrayDeque<Object> writeQueue = new ArrayDeque<>();
    private final ObjectManager manager = new ObjectManager();
    private final Writer writer = new Writer();
    private int currentObjectId = -1;

    private int getRedirectionId(Object value) {
        Integer newObjectId = manager.getId(value);
        if (newObjectId == null) {
            writeQueue.add(value);
            newObjectId = currentObjectId + writeQueue.size();
            manager.setId(value, newObjectId);
        }

        return newObjectId;
    }

    public void serialize(Object obj, String filename) throws IOException {
        writer.startFile(new FileOutputStream(filename));
        // So that we can safely continue out of everywhere
        // First object will be primary, other not primary.
        var isPrimary = true;

        writeQueue.add(obj);

        while (!writeQueue.isEmpty()) {
            currentObjectId += 1;
            var currentObject = writeQueue.removeFirst();
            var clazz = currentObject.getClass();

            try {
                writer.startNewObject();
                writer.setMetaField("primary", isPrimary);
                writer.setMetaField("className", clazz.getName());
                writer.setMetaField("id", currentObjectId);
                manager.setId(currentObject, currentObjectId);

                var fields = clazz.getDeclaredFields();
                for (var field : fields) {
                    field.setAccessible(true);

                    // writer.setMetaField("type-" + field.getName(), field.getType().toString());

                    var value = field.get(currentObject);
                    var type = field.getType();
                    if (isPrimitive(value, type)) {
                        writer.setPrimitiveField(field.getName(), value);
                        continue;
                    }

                    if (type.isInterface()) {
                        writer.addRedirection(field.getName(), getRedirectionId(value));
                        continue;
                    }

                    if (type.isArray()) {
                        writer.startArrayField(field.getName());

                        int length = Array.getLength(value);
                        var elementType = type.getComponentType();
                        for (int i = 0; i < length; i++) {
                            var elementValue = Array.get(value, i);
                            if (isPrimitive(elementValue, elementType)) {
                                writer.addArrayPrimitive(elementValue);
                            } else {
                                 // if (elementValue.getClass().isInterface()) {
                                    writer.addSimpleRedirection(getRedirectionId(elementValue));
                                 // }
                            }
                        }

                        writer.endArray();
                    }

                    field.setAccessible(false);
                }

                writer.endObject();
            } catch (Exception e) {
                System.out.println("Failed to serialize: " + e);
            }

            isPrimary = false;
        }
    }

    public void finish() throws IOException {
        writer.finish();
    }

    // Return null if not primitive
    private static boolean isPrimitive(Object value, Class<?> type) {
        return value == null || type.isPrimitive() || type.equals(String.class);
    }
}
