package com.example.clothesstoreagent.playground.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the other common advice types besides {@code @Around}.
 *
 * This is intentionally scoped to the Playground feature only using the same marker annotation
 * as {@link PlaygroundDemoAspect}.
 *
 * Advice types included:
 * - {@code @Before}: runs right before the method executes
 * - {@code @AfterReturning}: runs only when the method returns successfully
 * - {@code @AfterThrowing}: runs only when the method throws an exception
 * - {@code @After}: runs "finally" (after return OR throw)
 *
 * Notes for learning:
 * - These advices cannot replace the return value (that's what {@code @Around} is for).
 * - For reactive return types (Mono/Flux), these advices observe the *method call* that returns
 *   the Publisher, not the later async execution. For reactive timing, use the {@code @Around}
 *   aspect that decorates Mono/Flux with Reactor operators.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class PlaygroundLifecycleAdvicesAspect {

    private static final Logger logger = LoggerFactory.getLogger(PlaygroundLifecycleAdvicesAspect.class);

    /**
     * A named reusable pointcut.
     *
     * Equivalent to: "methods in classes annotated with @PlaygroundAopDemo OR methods annotated with it".
     */
    @Pointcut("@within(com.example.clothesstoreagent.playground.aop.PlaygroundAopDemo) || "
            + "@annotation(com.example.clothesstoreagent.playground.aop.PlaygroundAopDemo)")
    public void playgroundAopScope() {
        // Pointcut method body is empty by design.
    }

    @Before("playgroundAopScope()")
    public void before(JoinPoint joinPoint) {
        logger.info("[AOP][Playground][Before] {}", joinPoint.getSignature().toShortString());
    }

    @AfterReturning(pointcut = "playgroundAopScope()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        logger.info("[AOP][Playground][AfterReturning] {} returnedType={}",
                joinPoint.getSignature().toShortString(), result == null ? "null" : result.getClass().getSimpleName());
    }

    @AfterThrowing(pointcut = "playgroundAopScope()", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Throwable ex) {
        logger.warn("[AOP][Playground][AfterThrowing] {} message={}",
                joinPoint.getSignature().toShortString(), ex.getMessage());
    }

    @After("playgroundAopScope()")
    public void afterFinally(JoinPoint joinPoint) {
        logger.info("[AOP][Playground][After(finally)] {}", joinPoint.getSignature().toShortString());
    }
}
