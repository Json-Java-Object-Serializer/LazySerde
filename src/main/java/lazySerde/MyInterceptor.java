package lazySerde;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class MyInterceptor implements MethodInterceptor {

    private final Deserializer deserializer;
    private final Map<Object, Map<String, Integer>> redirections;
    private final Map<Object, Map<String, List<Integer>>> arrayRedirections;
    private boolean loaded = false;

    MyInterceptor(Deserializer deserializer) {
        redirections = new IdentityHashMap<>();
        arrayRedirections = new IdentityHashMap<>();
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
        var arrays = arrayRedirections.get(obj);
        if (arrays == null) return;
        for (var entry : arrays.entrySet()) {
            var list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == null) continue;
                setArrayItem(obj, entry.getKey(), i, list.get(i));
            }
        }
    }

    private Field getField(Object obj, String fieldName) throws Exception {
        try {
            // In case non-lazy loaded class has some fields
            return obj.getClass().getDeclaredField(fieldName);
        } catch (Exception e) {
            return obj.getClass().getSuperclass().getDeclaredField(fieldName);
        }
    }

    public void setFieldRedirection(Object obj, String fieldName, Integer id) {
        if (!redirections.containsKey(obj)) {
            redirections.put(obj, new HashMap<>());
        }
        redirections.get(obj).put(fieldName, id);
    }

    public void setField(Object obj, String fieldName, Integer id) throws Exception {
        var field = getField(obj, fieldName);
        boolean isLazy = field.getType().isInterface();
        field.setAccessible(true);
        field.set(obj, deserializer.readObject(id, isLazy));
        field.setAccessible(false);
    }


    public void setArrayRedirection(Object obj, String fieldName, Integer idx, Integer id) throws Exception {
        if (!arrayRedirections.containsKey(obj)) {
            arrayRedirections.put(obj, new HashMap<>());
        }
        var objArrays = arrayRedirections.get(obj);
        if (!objArrays.containsKey(fieldName)) {
            objArrays.put(fieldName, new ArrayList<>());
        }
        var redirectionList = objArrays.get(fieldName);

        // Didn't find .resize or equivalent
        while (redirectionList.size() <= idx) {
            redirectionList.add(null);
        }
        redirectionList.set(idx, id);
    }

    public void setArrayItem(Object obj, String fieldName, Integer idx, Integer id) throws Exception {
        var field = getField(obj, fieldName);
        boolean isLazy = field.getType().getComponentType().isInterface();
        field.setAccessible(true);
        Array.set(field.get(obj), idx, deserializer.readObject(id, isLazy));
        field.setAccessible(false);
    }
}
