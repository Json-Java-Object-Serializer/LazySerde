package lazySerde;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;

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

    public Serializer(String filename) throws IOException {
        writer.startFile(new FileOutputStream(filename));
    }

    public void serialize(Object obj) {
        var isPrimary = true;

        writeQueue.add(obj);

        while (!writeQueue.isEmpty()) {
            currentObjectId += 1;
            var currentObject = writeQueue.removeFirst();
            var clazz = currentObject.getClass();

            try {
                writer.startNewObject();
                writer.setMetaField("className", clazz.getName());
                writer.setMetaField("id", currentObjectId);
                writer.setMetaField("primary", isPrimary);
                manager.setId(currentObject, currentObjectId);

                var fields = clazz.getDeclaredFields();
                for (var field : fields) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
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
                        for (int i = 0; i < length; i++) {
                            var elementValue = Array.get(value, i);
                            if (elementValue == null) {
                                writer.addArrayPrimitive(elementValue);
                            } else
                            if (isPrimitive(elementValue, elementValue.getClass())) {
                                writer.addArrayPrimitive(elementValue);
                            } else {
                                if (elementValue.getClass().isInterface()) {
                                    writer.addSimpleRedirection(getRedirectionId(elementValue));
                                }
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
        return value == null || type.isPrimitive() || type.equals(String.class) ||
                type.equals(Integer.class) || type.equals(Short.class) || type.equals(Long.class) || type.equals(Byte.class)
                || type.equals(Boolean.class) || type.equals(Character.class) || type.equals(Double.class) || type.equals(Float.class);
    }
}
