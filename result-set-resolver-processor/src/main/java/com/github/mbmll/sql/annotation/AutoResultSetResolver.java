package com.github.mbmll.sql.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE) // 仅编译期生效，编译后丢弃
@Documented
public @interface AutoResultSetResolver {
}

