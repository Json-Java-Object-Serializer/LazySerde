package lazySerde;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MyInterceptor implements MethodInterceptor {

    private final Deserializer deserializer;
    private final HashMap<String, Integer> redirections = new HashMap<>();
    private boolean loaded = false;

    MyInterceptor(Deserializer deserializer) {
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
        for (Map.Entry<String, Integer> entry : redirections.entrySet()) {
            Field field = obj.getClass().getSuperclass().getDeclaredField(entry.getKey());

            field.setAccessible(true);
            field.set(obj, deserializer.readObject(entry.getValue()));
            field.setAccessible(false);
        }
    }

    public void setFieldRedirection(String fieldName, Integer id) {
        redirections.put(fieldName, id);
    }


    public void setArrayRedirection(String fieldName, Integer idx, Integer id) {
        // TODO: ...

    }
}
