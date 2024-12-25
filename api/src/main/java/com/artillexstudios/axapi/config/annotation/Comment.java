package com.artillexstudios.axapi.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Comment {

    String value();

    CommentType type() default CommentType.BLOCK;

    enum CommentType {
        BLOCK,
        INLINE
    }
}
