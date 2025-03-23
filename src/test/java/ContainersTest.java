import lazySerde.Deserializer;
import lazySerde.SampleClass;
import lazySerde.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContainersTest {

    @Test
    void simpleArrayListTest() throws Exception {
        ArrayList<Integer> arrayList = new ArrayList<>();
        arrayList.add(4);
        arrayList.add(7);
        arrayList.add(99);

        Serializer serializer = new Serializer("out.json");
        serializer.serialize(arrayList);
        serializer.finish();

        System.out.println("ArrayList deserialization started");
        Deserializer deserializer = new Deserializer();
        deserializer.index(Path.of("out.json"));
        ArrayList<Integer> processed = (ArrayList<Integer>) deserializer.getNext(ArrayList.class);
        System.out.println("ArrayList deserialization ended");

        Assertions.assertEquals(arrayList.size(), processed.size());
        for(int i = 0; i < arrayList.size(); i++) {
            Assertions.assertEquals(arrayList.get(i), processed.get(i));
        }
    }

    @Test
    void simpleListTest() throws Exception {
        List<Integer> arrayList = new ArrayList<>();
        arrayList.add(4);
        arrayList.add(7);
        arrayList.add(99);


        Serializer serializer = new Serializer("out.json");
        serializer.serialize(arrayList);
        serializer.finish();

        System.out.println("Interface List deserialization started");
        Deserializer deserializer = new Deserializer();
        deserializer.index(Path.of("out.json"));
        List<Integer> processed = (List<Integer>) deserializer.getNext(List.class);
        System.out.println("Interface List deserialization ended");

        Assertions.assertEquals(arrayList.size(), processed.size());
        for(int i = 0; i < arrayList.size(); i++) {
            Assertions.assertEquals(arrayList.get(i), processed.get(i));
        }
    }
}
