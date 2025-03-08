package lazySerde;

import java.util.IdentityHashMap;

public class ObjectManager {

    private final IdentityHashMap<Object, Integer> objects = new IdentityHashMap<Object, Integer>();

    public Integer getId(Object value) {
        return objects.get(value);
    }

    public void setId(Object value, Integer newObjectId) {
        objects.put(value, newObjectId);
    }
}
