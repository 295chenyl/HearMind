package com.dovidioai.service;

import com.dovidioai.exception.UnauthorizedException;
import com.dovidioai.security.UserContext;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

    public String username() {
        UserContext.AuthUser user = UserContext.get();
        return user != null ? user.username() : null;
    }
}
