package lazySerde;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

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
        sample.primitiveWrapper = -123;
        sample.qowieuq = false;
        sample.list = new ArrayList<>();
        sample.list.add(5);
        sample.list.add(3);
        sample.list.add(null);
        sample.list.add(4);
        sample.sndfmsdfh = "Simple String";
        sample.sndfmsdfh2 = "\"Complex\" String";
        sample.notHandled = new ArrayList<>();
        sample.handled = new Integer[5];
        for (int i = 0; i < sample.handled.length; i++) {
            sample.handled[i] = 8 * i;
            sample.notHandled.add(9 * i * i);
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
        sample2.qowieuq = true;
        sample2.primitiveWrapper = 19;
        sample2.sndfmsdfh = "Sile String";
        sample2.sndfmsdfh2 = "omplex\" String";
        sample2.notHandled = new ArrayList<>();
        sample2.handled = new Integer[5];
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

        /*
        VM options:
        --add-opens java.base/java.util=ALL-UNNAMED
        --add-opens java.base/java.lang=ALL-UNNAMED
         */
        Serializer serializer = new Serializer("out.json");
        serializer.serialize(sample);
        serializer.serialize(sample2);
        serializer.finish();

        Deserializer deserializer = new Deserializer();
        deserializer.index(Path.of("out.json"));

        SampleClass cur = (SampleClass)deserializer.getNext(SampleClass.class, (proxy) -> {
            try {
                System.out.println(proxy.getField("qowieuq"));
                return (boolean)proxy.getField("qowieuq");
            } catch (Exception e) {
                return false;
            }
        });
        System.out.println(cur.qowieuq);
        System.out.println(cur.sndfmsdfh);
//        var proxy = deserializer.createProxy(0);
//        System.out.println(proxy.getField("value1"));
//        System.out.println(proxy.getField("woieur"));
//        System.out.println(proxy.getField("sndfmsdfh"));
//        System.out.println(Arrays.toString((Integer[])proxy.getField("handled")));
//        var array = proxy.getField("notHandled");
//        System.out.println(array.toString());
//        SampleClass cur = (SampleClass)deserializer.getNext(SampleClass.class);
//        System.out.println("Object read");
//        System.out.println(cur.primitiveWrapper);
//        System.out.println(Arrays.toString(cur.handled));
//        System.out.println(cur.list.get(0));
    }

}
