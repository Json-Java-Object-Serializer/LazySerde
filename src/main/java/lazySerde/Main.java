package lazySerde;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        var sample = new SampleClass();
        sample.io2wur = 2;
        sample.mnn = 1;
        sample.value1 = -1;
        sample.value2 = 15.0f;
        sample.aha = 13.2;
        sample.sldkjfsldjf = 1123123;
        sample.woieur = 'c';
        sample.list = new ArrayList<Integer>();
        sample.list.add(5);
        sample.list.add(3);
        sample.sndfmsdfh = "Simple String";
        sample.sndfmsdfh2 = "\"Complex\" String";
        sample.notHandled = new ArrayList<>();
        sample.handled = new int[5];
        for (int i = 0; i < sample.handled.length; i++) {
            sample.handled[i] = 8 * i;
        }
        sample.handledString = new String[3];
        sample.handledString[0] = "wow";
        sample.handledString[1] = "\"that";
        sample.handledString[2] = "actually works\"";

        var sample2 = new SampleClass();
        sample2.io2wur = -2;
        sample2.mnn = -1;
        sample2.value1 = 1;
        sample2.value2 = 19.0f;
        sample2.aha = 13.0;
        sample2.sldkjfsldjf = 1123;
        sample2.woieur = 'i';
        sample2.sndfmsdfh = "Sile String";
        sample2.sndfmsdfh2 = "omplex\" String";
        sample2.notHandled = new ArrayList<>();
        sample2.handled = new int[5];
        for (int i = 0; i < sample2.handled.length; i++) {
            sample2.handled[i] = 2 * i;
        }
        sample2.handledString = new String[3];
        sample2.handledString[0] = "WoW";
        sample2.handledString[1] = "\"HHHHH";
        sample2.handledString[2] = "slkjfslkdjfklsdjfklsdj";

        sample.secondOne = new SampleClass[1];
        sample.secondOne[0] = sample2;
        sample2.secondOne = new SampleClass[1];
        sample2.secondOne[0] = sample;

        Serializer serializer = new Serializer("out.json");
        serializer.serialize(sample);
        serializer.serialize(sample2);
        serializer.finish();

        Deserializer deserializer = new Deserializer();
        deserializer.index(Path.of("out.json"));

        Object cur;
        do {
            cur = deserializer.getNext(SampleClass.class);
            System.out.println(cur);
        } while (cur != null);

    }
}
