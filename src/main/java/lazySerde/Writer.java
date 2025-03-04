package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;

public class Writer {

    private final JsonGenerator jsonGenerator;
    private final int indent = 4;

    public Writer(OutputStream output) throws IOException {
        // Create a JsonFactory and configure it for pretty printing
        JsonFactory jsonFactory = new JsonFactory();
        jsonGenerator = jsonFactory.createGenerator(output);
        jsonGenerator.useDefaultPrettyPrinter();

        // Start the JSON structure
        jsonGenerator.writeStartObject(); // Start root object
        jsonGenerator.writeFieldName("serialization");
        jsonGenerator.writeStartArray(); // Start the "serialization" array
    }

    public void startNewObject() throws IOException {
        jsonGenerator.writeStartObject(); // Start a new nested object
    }

    public void endObject() throws IOException {
        jsonGenerator.writeEndObject(); // End the current object
    }

    public void setPrimitiveField(String fieldName, Object primitive) throws IOException {
        jsonGenerator.writeFieldName(fieldName); // Write field name
        if (primitive == null) {
            jsonGenerator.writeNull(); // Write null value
        } else if (primitive instanceof String || primitive instanceof Character) {
            jsonGenerator.writeString(primitive.toString()); // Write string value
        } else {
            jsonGenerator.writeNumber(primitive.toString()); // Write number value
        }
    }

    public void setMetaField(String fieldName, Object info) throws IOException {
        setPrimitiveField("@" + fieldName, info); // Write meta field with "@" prefix
    }

    public void addRedirection(String fieldName, int id) throws IOException {
        jsonGenerator.writeFieldName(fieldName); // Write field name
        startNewObject(); // Start a new object for redirection
        setMetaField("redirect", id); // Add the "redirect" meta field
        endObject(); // End the redirection object
    }

    public void startArrayField(String fieldName) throws IOException {
        jsonGenerator.writeFieldName(fieldName); // Write field name
        jsonGenerator.writeStartArray(); // Start a new array
    }

    public void addArrayPrimitive(Object elementValue) throws IOException {
        if (elementValue == null) {
            jsonGenerator.writeNull(); // Write null value
        } else if (elementValue instanceof String || elementValue instanceof Character) {
            jsonGenerator.writeString(elementValue.toString()); // Write string value
        } else {
            jsonGenerator.writeNumber(elementValue.toString()); // Write number value
        }
    }

    public void endArray() throws IOException {
        jsonGenerator.writeEndArray(); // End the current array
    }

    public void addSimpleRedirection(int id) throws IOException {
        startNewObject(); // Start a new object
        setMetaField("redirect", id); // Add the "redirect" meta field
        endObject(); // End the object
    }

    public void finish() throws IOException {
        jsonGenerator.writeEndArray(); // End the "serialization" array
        jsonGenerator.writeEndObject(); // End the root object
        jsonGenerator.close(); // Close the JsonGenerator
    }
}