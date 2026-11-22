package org.xidian.program325.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.xidian.program325.pojo.Account.Role;
import org.xidian.program325.pojo.Account.SessionUser;

public final class Sessions {
    private static final String USER = Sessions.class.getName() + ".user";

    private Sessions() {
    }

    public static SessionUser requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(USER) instanceof SessionUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
    }

    public static SessionUser requireAdmin(HttpServletRequest request) {
        SessionUser user = requireUser(request);
        if (user.role() != Role.ADMIN) {
            throw ApiException.forbidden("需要管理员权限");
        }
        return user;
    }

    public static void login(HttpServletRequest request, SessionUser user) {
        logout(request);
        request.getSession(true).setAttribute(USER, user);
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
