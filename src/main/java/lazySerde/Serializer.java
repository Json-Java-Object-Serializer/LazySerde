package lazySerde;

import org.apache.commons.text.StringEscapeUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;

public class Serializer {
    public static void serialize(Object obj, String filename) {
        // TODO: Write to file
        var toWrite = new ArrayDeque<>();
        toWrite.add(obj);
        // So that we can safely continue out of everywhere
        var currentObjectId = -1;
        while (!toWrite.isEmpty()) {
            currentObjectId += 1;
            var currentObject = toWrite.removeFirst();
            var clazz = currentObject.getClass();
            // Complex type case
            // I think we can handle simple types without getter/setters bc no laziness is required
            try {
                System.out.println("{");
                // TODO: handle inherited fields
                var fields = clazz.getDeclaredFields();
                System.out.printf("\t\"__type__\": %s%n", clazz.getName());
                System.out.printf("\t\"__id__\": %s%n", currentObjectId);
                for (var field : fields) {
                    field.setAccessible(true);
                    var type = field.getType();
                    var value = field.get(currentObject);
                    var primitive = printIfPrimitive(value, type);
                    if (primitive != null) {
                        System.out.printf("\t\"%s\": ", field.getName());
                        System.out.printf("%s,%n", primitive);
                        continue;
                    }
                    if (type.isArray()) {
                        System.out.printf("\t\"%s\": ", field.getName());
                        System.out.print("[");
                        int length = Array.getLength(value);
                        var elementType = type.getComponentType();
                        if (isPrimitive(elementType)) {
                            for (int i = 0; i < length; i++) {
                                var elementValue = Array.get(value, i);
                                primitive = printIfPrimitive(elementValue, elementType);
                                assert primitive != null;
                                System.out.printf("%s", primitive);
                                if (i < length - 1) {
                                    System.out.print(",");
                                }
                            }
                        } else {
                            // Can we handle this case?
                            // We probably can override .get() on ArrayList, but element access on arrays?
                            for (int i = 0; i < length; i++) {
                                var elementValue = Array.get(value, i);
                                if (elementValue == null) {
                                    System.out.print("null");
                                } else {
                                    toWrite.add(elementValue);
                                    var newObjectId = currentObjectId + toWrite.size();
                                    System.out.printf("{ \"__id__\": %s }", newObjectId);
                                }
                                if (i < length - 1) {
                                    System.out.print(",");
                                }
                            }
                        }
                        System.out.println("],");
                    }

                }
                // TODO: handle getters/setters:
                /*
                toWrite.add(value);
                var newObjectId = currentObjectId + toWrite.size();
                System.out.printf("\t\"%s\": { \"__id__\": %s }", field.getName(), newObjectId);
                */
                System.out.println("}");
            } catch (Exception e) {
                System.out.println("Failed to serialize: " + e);
            }
        }
    }

    // Return null if not primitive
    private static String printIfPrimitive(Object value, Class<?> type) {
        if (value == null)
            return "null";
        if (type.isPrimitive() && !type.getName().equals("char"))
            return value.toString();
        if (type.getName().equals(String.class.getName()) || type.getName().equals("char"))
            return String.format("\"%s\"", StringEscapeUtils.escapeJson(value.toString()));
        return null;
    }

    private static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive() || type.getName().equals(String.class.getName());
    }
}
