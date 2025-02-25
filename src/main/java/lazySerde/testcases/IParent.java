package lazySerde.testcases;

public interface IParent {

    int some_constant = 5;

    String getName();

    String setName(String name);

    void addChild(Child child);

    Child[] getChildren();
}
