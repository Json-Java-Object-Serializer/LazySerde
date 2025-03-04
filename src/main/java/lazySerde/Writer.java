package lazySerde;

import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Writer {

    private final int indent = 4;
    private int currentOffset = 0;
    private OutputStream output;
    private void handleOffset() throws IOException {
        for(int i = 0; i < currentOffset; i++) {
            print(" ");
        }
    }

    public Writer(OutputStream output) throws IOException {
        this.output = output;
        startNewObject();
        startArrayField("serialization");
    }

    public void startNewObject() throws IOException {
        handleOffset();
        print("{\n");
        currentOffset += indent;
    }

    public void endObject() throws IOException {
        currentOffset -= indent;
        handleOffset();
        print("},\n");
    }

    public void setPrimitiveField(String fieldName, Object primitive) throws IOException {
        handleOffset();
        print(String.format("\"%s\": %s,\n", fieldName, formatPrimitive(primitive)));
    }

    private void print(String str) throws IOException {
        output.write(str.getBytes(StandardCharsets.UTF_8));
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

    public void setMetaField(String fieldName, Object info) throws IOException {
        setPrimitiveField("@" + fieldName, info);
    }

    public void addRedirection(String fieldName, int id) throws IOException {
        handleOffset();
        print(String.format("\"%s\": ", fieldName));
        addSimpleRedirection(id);
    }

    public void startArrayField(String fieldName) throws IOException {
        handleOffset();
        print(String.format("\"%s\": [\n", fieldName));
        currentOffset += indent;
    }

    public void addArrayPrimitive(Object elementValue) throws IOException {
        handleOffset();
        print(String.format(formatPrimitive(elementValue) + ",\n" ));
    }

    public void endArray() throws IOException {
        currentOffset -= indent;
        handleOffset();
        print("],\n");
    }

    public void addSimpleRedirection(int id) throws IOException {
        startNewObject();
        setMetaField("redirect", id);
        endObject();
    }

    public void finish() throws IOException {
        endArray();
        endObject();
    }
}