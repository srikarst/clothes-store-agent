package com.example.clothesstoreagent.playground.aop;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class PlaygroundDemoAspect {

    /*
     * =========================
     * AOP learning notes (demo)
     * =========================
     *
     * 1) What does @Order(...) do?
     *    - When multiple aspects apply to the same join point (method execution), Spring uses @Order
     *      to decide which aspect runs first.
     *    - Lower order value = higher precedence.
     *    - With @Around advice, aspects nest like onion layers:
     *         Outer aspect (runs first) -> proceed() -> inner aspect -> target method -> unwind.
     *    - Here we use very high precedence so this demo aspect tends to wrap other aspects.
     *
     * 2) What does @Around mean?
     *    - @Around is an advice type that *wraps* the target method.
     *    - This method can run code before and after the target method by calling joinPoint.proceed().
     *    - It can also replace/modify the return value (we do that for Mono/Flux).
     *
     * 3) What does @within(...) || @annotation(...) mean?
     *    - This is an AspectJ pointcut expression.
     *    - @within(PlaygroundAopDemo) matches methods where the *declaring class* is annotated.
     *      Example: @PlaygroundAopDemo on PlaygroundReactiveController -> all its methods match.
     *    - @annotation(PlaygroundAopDemo) matches methods where the *method itself* is annotated.
     *      Example: annotate only one endpoint method -> only that method matches.
     *    - Using OR (||) gives you both options: annotate a whole class OR annotate individual methods.
     *
     * 4) Why do we use Reactor operators like doOnSubscribe here?
     *    - For reactive return types (Mono/Flux), the controller/service method usually returns
     *      immediately with a Publisher. The real work happens later on subscription.
     *    - So for "real" timing we attach callbacks:
     *        doOnSubscribe -> when execution starts
     *        doOnSuccess/doOnComplete -> when execution ends normally
     *        doOnError -> when it fails
     */

    private static final Logger logger = LoggerFactory.getLogger(PlaygroundDemoAspect.class);

    @Around("@within(com.example.clothesstoreagent.playground.aop.PlaygroundAopDemo) || @annotation(com.example.clothesstoreagent.playground.aop.PlaygroundAopDemo)")
    public Object aroundPlayground(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        String args = abbreviateArgs(joinPoint.getArgs());

        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();

            if (result instanceof Mono<?> mono) {
                AtomicLong subscribeStart = new AtomicLong(-1L);
                return mono
                        .doOnSubscribe(s -> {
                            subscribeStart.set(System.nanoTime());
                            logger.info("[AOP][Playground] SUBSCRIBE {} args={}", signature, args);
                        })
                        .doOnSuccess(v -> logger.info("[AOP][Playground] SUCCESS {} durationMs={} (from subscribe)", signature,
                                elapsedMs(subscribeStart.get())))
                        .doOnError(ex -> logger.warn("[AOP][Playground] ERROR {} durationMs={} (from subscribe) message={}", signature,
                                elapsedMs(subscribeStart.get()), safeMessage(ex), ex));
            }

            if (result instanceof Flux<?> flux) {
                AtomicLong subscribeStart = new AtomicLong(-1L);
                return flux
                        .doOnSubscribe(s -> {
                            subscribeStart.set(System.nanoTime());
                            logger.info("[AOP][Playground] SUBSCRIBE {} args={}", signature, args);
                        })
                        .doOnComplete(() -> logger.info("[AOP][Playground] COMPLETE {} durationMs={} (from subscribe)", signature,
                                elapsedMs(subscribeStart.get())))
                        .doOnError(ex -> logger.warn("[AOP][Playground] ERROR {} durationMs={} (from subscribe) message={}", signature,
                                elapsedMs(subscribeStart.get()), safeMessage(ex), ex));
            }

            logger.info("[AOP][Playground] RETURN {} durationMs={} args={}", signature, elapsedMs(startNanos), args);
            return result;
        } catch (Throwable ex) {
            logger.warn("[AOP][Playground] THROW {} durationMs={} args={} message={}", signature, elapsedMs(startNanos), args,
                    safeMessage(ex), ex);
            throw ex;
        }
    }

    private static long elapsedMs(long startNanos) {
        if (startNanos <= 0) {
            return -1L;
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "";
        }
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    private static String abbreviateArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < args.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(abbreviateArg(args[index]));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String abbreviateArg(Object arg) {
        if (arg == null) {
            return "null";
        }

        String stringValue;
        try {
            stringValue = String.valueOf(arg);
        } catch (RuntimeException ex) {
            return arg.getClass().getSimpleName() + "(toString failed)";
        }

        int maxLen = 160;
        if (stringValue.length() > maxLen) {
            return stringValue.substring(0, maxLen) + "…";
        }
        return stringValue;
    }
}
