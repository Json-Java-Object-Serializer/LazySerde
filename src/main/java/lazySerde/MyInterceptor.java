package lazySerde;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class MyInterceptor implements MethodInterceptor {
    private record ArrayData(int length, List<Integer> redirections) {
    }

    private final Deserializer deserializer;
    private final Map<Object, Map<String, Integer>> redirections;
    private final Map<Object, Map<String, ArrayData>> arrayData;
    private boolean loaded = false;

    MyInterceptor(Deserializer deserializer) {
        redirections = new IdentityHashMap<>();
        arrayData = new IdentityHashMap<>();
        this.deserializer = deserializer;
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        if (!loaded) {
            loadObject(obj);
            loaded = true;
        }
        return proxy.invokeSuper(obj, args); // Call the original method
    }

    private void loadObject(Object obj) throws Exception {
        var objRedirections = redirections.get(obj);
        if (objRedirections != null) {
            for (var entry : objRedirections.entrySet()) {
                setField(obj, entry.getKey(), entry.getValue());
            }
        }
        var arrays = arrayData.get(obj);
        if (arrays == null) return;
        for (var entry : arrays.entrySet()) {
            var arrayName = entry.getKey();
            var data = entry.getValue();
            var field = Utils.getField(obj.getClass(), arrayName);
            field.setAccessible(true);
            field.set(obj, Array.newInstance(field.getType().getComponentType(), data.length));
            field.setAccessible(false);
            for (int i = 0; i < data.redirections.size(); i++) {
                if (data.redirections.get(i) == null) continue;
                setArrayItem(obj, entry.getKey(), i, data.redirections.get(i));
            }
        }
    }

    public void setFieldRedirection(Object obj, String fieldName, Integer id) {
        if (!redirections.containsKey(obj)) {
            redirections.put(obj, new HashMap<>());
        }
        redirections.get(obj).put(fieldName, id);
    }

    public void setField(Object obj, String fieldName, Integer id) throws Exception {
        var field = Utils.getField(obj.getClass(), fieldName);
        boolean isLazy = field.getType().isInterface();
        field.setAccessible(true);
        field.set(obj, deserializer.readObject(id, isLazy));
        field.setAccessible(false);
    }

    public void setArraySize(Object obj, String fieldName, Integer length) {
        if (!arrayData.containsKey(obj)) {
            arrayData.put(obj, new HashMap<>());
        }
        var arrays = arrayData.get(obj);
        arrays.put(fieldName, new ArrayData(length, new ArrayList<>()));
    }

    public void setArrayRedirection(Object obj, String fieldName, Integer idx, Integer id) {
        var array = arrayData.get(obj).get(fieldName);

        // Didn't find .resize or equivalent
        while (array.redirections.size() <= idx) {
            array.redirections.add(null);
        }
        array.redirections.set(idx, id);
    }

    public void setArrayItem(Object obj, String fieldName, Integer idx, Integer id) throws Exception {
        var field = Utils.getField(obj.getClass(), fieldName);
        boolean isLazy = field.getType().getComponentType().isInterface();
        field.setAccessible(true);
        Array.set(field.get(obj), idx, deserializer.readObject(id, isLazy));
        field.setAccessible(false);
    }
}
