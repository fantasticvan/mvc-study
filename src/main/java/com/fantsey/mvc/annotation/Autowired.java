package com.fantsey.mvc.annotation;

import java.lang.annotation.*;

/**
 * 将ioc中获取的bean，注入到指定类中
 * @author fantsey
 * @date 2019/6/25
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {
    String value() default "";
}
