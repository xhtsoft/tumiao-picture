package com.xhtsoft.tumiaopicturebackend.aop;

import com.xhtsoft.tumiaopicturebackend.annotation.AuthCheck;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.UserRoleEnum;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 获取必须的角色
        String mustRole = authCheck.mustRole();
        // 2. 获取当前用户的角色
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        String userRole = loginUser.getUserRole();
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 3. 不需要权限,放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 4. 当前用户没有权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        ThrowUtil.throwIf(userRoleEnum == null, ErrorCode.NO_AUTH_ERROR);
        // 5. 判断必须角色是否是管理员以及当前角色是否是管理员
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
