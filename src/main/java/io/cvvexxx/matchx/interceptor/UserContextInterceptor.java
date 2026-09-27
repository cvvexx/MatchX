package io.cvvexxx.matchx.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private final static String X_USER_ID = "X-User-Id";

    public static final ThreadLocal<UUID> USER_CONTEXT = new ThreadLocal<>();

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        String userIdHeader = request.getHeader(X_USER_ID);
        if (userIdHeader != null) {
            USER_CONTEXT.set(UUID.fromString(userIdHeader));
        }
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        USER_CONTEXT.remove();
    }
}