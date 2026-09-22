package com.exdemo.admin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOperation {

    /** 操作模块：USER / PRODUCT / ORDER / SYSTEM */
    String module();

    /** 操作描述，如"禁用用户" */
    String action();
}
