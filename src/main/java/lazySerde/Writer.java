package lazySerde;

import org.apache.commons.text.StringEscapeUtils;

public class Writer {

    private final int indent = 4;
    private int currentOffset = 0;
    private void handleOffset() {
        for(int i = 0; i < currentOffset; i++) {
            System.out.print(" ");
        }
    }

    public Writer() {
        System.out.print("{\n" + "    \"serialization\": [\n");
        currentOffset += 2 * indent;
    }

    public void startNewObject() {
        handleOffset();
        System.out.print("{\n");
        currentOffset += indent;
    }

    public void endObject() {
        currentOffset -= indent;
        handleOffset();
        System.out.print("},\n");
    }

    public void setPrimitiveField(String fieldName, Object primitive) {
        handleOffset();
        System.out.printf("\"%s\": %s,\n", fieldName, formatPrimitive(primitive));
    }

    private String formatPrimitive(Object primitive) {
        if (primitive == null) {
            return "null";
        } else
        if (primitive.getClass().equals(String.class) || primitive.getClass().equals(Character.class)) {
            return String.format("\"%s\"", StringEscapeUtils.escapeJson(primitive.toString()));
        } else {
            return primitive.toString();
        }
    }

    public void setMetaField(String fieldName, Object info) {
        setPrimitiveField("@" + fieldName, info);
    }

    public void addRedirection(String fieldName, int id) {
        handleOffset();
        System.out.printf("\"%s\": ", fieldName);
        addSimpleRedirection(id);
    }

    public void startArrayField(String fieldName) {
        handleOffset();
        System.out.printf("\"%s\": [\n", fieldName);
        currentOffset += indent;
    }

    public void addArrayPrimitive(Object elementValue) {
        handleOffset();
        System.out.print(formatPrimitive(elementValue) + ",\n" );
    }

    public void endArray() {
        currentOffset -= indent;
        handleOffset();
        System.out.print("],\n");
    }

    public void addSimpleRedirection(int id) {
        startNewObject();
        setMetaField("redirect", id);
        endObject();
    }

    public void finish() {
        currentOffset -= 2 * indent;
        System.out.print("    ]\n}\n");
    }

}
