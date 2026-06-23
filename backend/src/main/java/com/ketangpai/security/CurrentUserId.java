package com.ketangpai.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户 ID 的注解。
 * 用于 Controller 方法参数，从 SecurityContext 中提取 userId。
 *
 * <pre>{@code
 * @GetMapping("/profile")
 * public Result<User> profile(@CurrentUserId Long userId) {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
