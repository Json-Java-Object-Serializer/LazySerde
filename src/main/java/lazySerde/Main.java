package gcla;

import java.io.Serial;

public class Main {
    public static void main(String[] args) {
        var sample = new SampleClass();
        sample.io2wur = 2;
        sample.mnn = 1;
        sample.value1 = -1;
        sample.value2 = 15.0f;
        sample.aha = 13.2;
        sample.sldkjfsldjf = 1123123;
        sample.woieur = 'c';
        sample.sndfmsdfh = "Simple String";
        sample.sndfmsdfh2 = "\"Complex\" String";
        Serializer.serialize(sample, "");
        System.out.println("Hello, World!");
    }
}