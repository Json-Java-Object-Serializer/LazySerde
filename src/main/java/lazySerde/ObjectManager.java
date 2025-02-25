package lazySerde;

import java.util.HashMap;

public class ObjectManager {

    private final HashMap<Object, Integer> objects = new HashMap<Object, Integer>();

    public Integer getId(Object value) {
        return objects.get(value);
    }

    public void setId(Object value, Integer newObjectId) {
        objects.put(value, newObjectId);
    }
}
