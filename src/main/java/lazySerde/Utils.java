package lazySerde;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Utils {
    public static List<Field> getAllFields(Class<?> clazz) {
        ArrayList<Field> res = new ArrayList<>();
        var currentClazz = clazz;
        do {
            res.addAll(Arrays.asList(currentClazz.getDeclaredFields()));
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return res;
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        Field res = null;
        var currentClazz = clazz;
        do {
            try {
                res = currentClazz.getDeclaredField(fieldName);
                break;
            } catch (Exception ignored) {}
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return res;
    }
}
