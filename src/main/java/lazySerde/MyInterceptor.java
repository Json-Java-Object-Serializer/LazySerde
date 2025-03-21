package lazySerde;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class MyInterceptor implements MethodInterceptor {

    private final Deserializer deserializer;
    private final Map<Object, Map<String, Integer>> redirections;
    private boolean loaded = false;

    MyInterceptor(Deserializer deserializer) {
        redirections = new IdentityHashMap<>();
        this.deserializer = deserializer;
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        if (!loaded) {
            System.out.println("Loading...");
            loadObject(obj);
            loaded = true;
        }
        return proxy.invokeSuper(obj, args); // Call the original method
    }

    private void loadObject(Object obj) throws Exception {
        var objRedirections = redirections.get(obj);
        for (var entry : objRedirections.entrySet()) {
            Field field = obj.getClass().getSuperclass().getDeclaredField(entry.getKey());

            field.setAccessible(true);
            field.set(obj, deserializer.readObject(entry.getValue()));
            field.setAccessible(false);
        }
    }

    public void setFieldRedirection(Object obj, String fieldName, Integer id) {
        if (!redirections.containsKey(obj)) {
            redirections.put(obj, new HashMap<>());
        }
        redirections.get(obj).put(fieldName, id);
    }

    public void setField(Object obj, String fieldName, Integer id) throws Exception {
        Field field = obj.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, deserializer.readObject(id, false));
        field.setAccessible(false);
    }


    public void setArrayRedirection(String fieldName, Integer idx, Integer id) {
        // TODO: ...

    }
}
