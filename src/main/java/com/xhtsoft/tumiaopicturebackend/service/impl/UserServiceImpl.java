package com.xhtsoft.tumiaopicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhtsoft.tumiaopicturebackend.constant.UserConstant;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.mapper.UserMapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.user.UserLoginRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.user.UserQueryRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.user.UserRegisterRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.UserRoleEnum;
import com.xhtsoft.tumiaopicturebackend.model.vo.UserLoginVO;
import com.xhtsoft.tumiaopicturebackend.model.vo.UserVO;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xhtsoft
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-05-30 19:49:17
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求dto
     * @return 用户id
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // 1. 校验参数
        ThrowUtil.throwIf(StrUtil.hasBlank(userRegisterRequest.getUserAccount(),
                userRegisterRequest.getUserPassword(),
                userRegisterRequest.getCheckPassword()), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtil.throwIf(userRegisterRequest.getUserAccount().length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtil.throwIf(userRegisterRequest.getUserPassword().length() < 8 ||
                        userRegisterRequest.getCheckPassword().length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码过短");
        ThrowUtil.throwIf(!userRegisterRequest.getUserPassword().equals(userRegisterRequest.getCheckPassword()),
                ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        // 2. 检查用户名是否已经存在数据库中
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userRegisterRequest.getUserAccount());
        long count = this.baseMapper.selectCount(queryWrapper);
        ThrowUtil.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "用户名已存在");
        // 3. 对密码进行加密
        String encryptPassword = getEncryptPassword(userRegisterRequest.getUserPassword());
        // 4. 将用户数据插入数据库中
        User user = new User();
        user.setUserAccount(userRegisterRequest.getUserAccount());
        user.setUserPassword(encryptPassword);
        user.setUserName("无名喵" + IdUtil.fastSimpleUUID().substring(0, 4));
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        ThrowUtil.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "用户注册失败");
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest   用户登录请求dto
     * @param httpServletRequest 请求
     * @return 用户信息
     */
    @Override
    public UserLoginVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest httpServletRequest) {
        // 1. 校验参数
        ThrowUtil.throwIf(StrUtil.hasBlank(userLoginRequest.getUserAccount(),
                userLoginRequest.getUserPassword()), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtil.throwIf(userLoginRequest.getUserAccount().length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtil.throwIf(userLoginRequest.getUserPassword().length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码错误");

        // 2. 对用户传输的密码进行加密
        String encryptPassword = getEncryptPassword(userLoginRequest.getUserPassword());
        // 3. 查询数据库，判断用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userLoginRequest.getUserAccount());
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("User login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }
        // 4. 保存用户的登录态
        httpServletRequest.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return userLoginVO;
    }

    /**
     * 获取当前登录用户
     *
     * @param request 请求
     * @return 用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 1. 从session中获取当前登录用户
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        ThrowUtil.throwIf(currentUser == null || currentUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 2. 去数据库中查询用户信息
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        ThrowUtil.throwIf(currentUser == null, ErrorCode.NOT_FOUND_ERROR);
        return currentUser;
    }

    /**
     * 用户登出
     *
     * @param request 请求
     * @return 登出结果
     */
    @Override
    public Boolean userLogout(HttpServletRequest request) {
        Object user = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtil.throwIf(user == null, ErrorCode.OPERATION_ERROR, "用户未登录");
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取加密后的密码
     *
     * @param userPassword 原始密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐，混淆密码
        final String SALT = "softbanana";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes());
    }

    /**
     * 获取脱敏的用户信息
     *
     * @param user 用户信息
     * @return 脱敏的用户信息
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) return null;
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏的用户信息列表
     *
     * @param userList 用户信息列表
     * @return 脱敏的用户信息列表
     */
    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    /**
     * 获取查询条件
     *
     * @param userQueryRequest 用户查询条件
     * @return 查询条件
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 是否为管理员
     *
     * @param user 用户信息
     * @return 是否为管理员
     */
    @Override
    public boolean isAdmin(User user) {
        ThrowUtil.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        return UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
}