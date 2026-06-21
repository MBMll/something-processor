package com.github.mbmll.sql.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE) // 仅编译期生效，编译后丢弃
@Documented
public @interface AutoResultSetResolver {
    // 可扩展：自定义数据库列名映射，驼峰下划线转换开关
    boolean camelToUnderline() default true;
}

