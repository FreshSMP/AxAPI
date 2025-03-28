package com.artillexstudios.axapi.reflection;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum ClassUtils {
    INSTANCE;

    private final Logger log = LoggerFactory.getLogger(ClassUtils.class);
    private final Unsafe unsafe = UnsafeUtils.INSTANCE.unsafe();
    private final Map<String, Boolean> CLASS_CACHE = new ConcurrentHashMap<>();

    public boolean classExists(@NotNull String className) {
        return CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                Class.forName(name, false, this.getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException exception) {
                return false;
            }
        });
    }

    public <T> T newInstance(String clazz) {
        try {
            return newInstance(Class.forName(clazz));
        } catch (ClassNotFoundException exception) {
            log.error("Could not find class {}!", clazz, exception);
            throw new RuntimeException(exception);
        }
    }

    public Class<?> getClass(String clazz) {
        try {
            return Class.forName(clazz);
        } catch (ClassNotFoundException exception) {
            log.error("An unexpected error occurred while finding class {}!", clazz, exception);
            throw new RuntimeException(exception);
        }
    }

    public Field getDeclaredField(String clazz, String field) {
        Class<?> cl = getClass(clazz);
        try {
            return cl.getDeclaredField(field);
        } catch (NoSuchFieldException exception) {
            log.error("An unexpected error occurred while getting field {} of class {}!", field, clazz, exception);
            throw new RuntimeException(exception);
        }
    }

    public <T> T newInstance(Class<?> clazz) {
        try {
            return (T) unsafe.allocateInstance(clazz);
        } catch (InstantiationException exception) {
            log.error("Failed to initialize new instance of class {}!", clazz.getName(), exception);
            throw new RuntimeException(exception);
        }
    }
}
