import org.springdoc.core.providers.JavadocProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 用 springdoc 自己的 Javadoc provider 读字段注释——JavadocPropertyCustomizer 调的就是 getFieldJavadoc，
 * 所以这里没有注释的字段，导进 Apifox 后也不会有属性描述。
 * 继承字段一并统计（BaseEntity 那四个会出现在每个实体的 schema 里）。
 */
public class FieldProbe {
    public static void main(String[] args) throws Exception {
        JavadocProvider p = new org.springdoc.core.providers.SpringDocJavadocProvider();
        List<String> missing = new ArrayList<>();
        int tot = 0;
        int doc = 0;
        for (String cn : args) {
            for (Class<?> c = Class.forName(cn); c != null && c != Object.class; c = c.getSuperclass()) {
                if (c.getName().startsWith("java.")) break;
                for (Field f : c.getDeclaredFields()) {
                    if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) continue;
                    tot++;
                    String d = p.getFieldJavadoc(f);
                    if (d != null && !d.isBlank()) {
                        doc++;
                    } else {
                        missing.add(c.getSimpleName() + "." + f.getName());
                    }
                }
            }
        }
        System.out.println("字段合计=" + tot + " 有描述=" + doc + " 缺=" + missing.size());
        if (!missing.isEmpty()) System.out.println("缺描述：" + String.join(", ", missing));
    }
}
