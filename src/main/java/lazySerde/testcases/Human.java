package lazySerde.testcases;

public class Human {

    private IHuman parent;
    private String name;
    private int age;
    // private children = []
    // siblings - arrayList
    //

    Human(String name, int age, IHuman parent) {
        this.parent = parent;
        this.name = name;
        this.age = age;
    }

    public String getParentName() {
        return parent.getName();
    }

    public String getName() {
        return name;
    }

}
