package lazySerde;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class Deserializer {
    private MappedByteBuffer mappedByteBuffer;
    private Map<Integer, Long> offsets;

    public void index(Path filename) throws IOException {
        getOffsetsWithIds(filename);
        System.out.println(offsets);

        try (FileChannel fileChannel = FileChannel.open(filename, StandardOpenOption.READ)) {
            this.mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        }
    }

    public void getOffsetsWithIds(Path filename) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        JsonParser jsonParser = jsonFactory.createParser(new File(String.valueOf(filename)));

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
}
