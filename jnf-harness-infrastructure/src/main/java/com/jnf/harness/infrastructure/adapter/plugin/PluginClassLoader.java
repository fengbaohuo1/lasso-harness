package com.jnf.harness.infrastructure.adapter.plugin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * 插件隔离类加载器：插件类 child-first，SPI/模型包与 JDK 共享 parent-first。
 * <p>共享前缀之外的类优先从插件制品加载——同一 FQCN 在宿主存在也不冲突。
 */
public class PluginClassLoader extends URLClassLoader {

    private static final String[] SHARED_PREFIXES = {
            "com.jnf.harness.model.", "com.jnf.harness.spi.",
            "java.", "javax.", "jakarta.", "org.slf4j.", "org.w3c.", "org.xml."
    };

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && !isShared(name)) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    // 回落 parent
                }
            }
            if (loaded == null) {
                loaded = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private static boolean isShared(String name) {
        for (String prefix : SHARED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
