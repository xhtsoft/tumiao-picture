package com.xhtsoft.tumiaopicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.mapper.SpaceMapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.space.SpaceAddRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.space.SpaceQueryRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.Space;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.SpaceLevelEnum;
import com.xhtsoft.tumiaopicturebackend.model.vo.SpaceVO;
import com.xhtsoft.tumiaopicturebackend.model.vo.UserVO;
import com.xhtsoft.tumiaopicturebackend.service.SpaceService;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 51404
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-07-26 15:34:12
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 创建空间
     *
     * @param spaceAddRequest 创建空间请求
     * @param loginUser       请求用户
     * @return 空间id
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. 填充参数默认值
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        this.validSpace(space, false);
        // 3. 校验权限，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "普通用户只能创建普通级别的空间！");
        }
        // 4. 控制同一个用户只能创建同一个私有空间
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long spaceId = transactionTemplate.execute(status -> {
                // 判断是否该用户已经创建过空间
                boolean exists = this.lambdaQuery().eq(Space::getUserId, userId)
                        .exists();
                // 如果已有空间则不能再创建空间
                ThrowUtil.throwIf(exists, ErrorCode.OPERATION_ERROR, "一个用户只能创建一个空间！");
                boolean result = this.save(space);
                ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建空间失败！");
                return space.getId();
            });
            return spaceId;
        }
    }

    /**
     * 获取空间包装类（单条）
     *
     * @param space   空间
     * @param request http请求
     * @return spaceVO
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage space分页数据
     * @param request   请求
     * @return VO分页数据
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 获取空间查询条件
     *
     * @param spaceQueryRequest 空间查询请求
     * @return 空间查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 构造查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 校验空间
     *
     * @param space 空间
     * @param add   是否为创建
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtil.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 创建时校验
        if (add) {
            ThrowUtil.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "创建空间时名字不能为空！");
            ThrowUtil.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "创建空间时级别不能为空！");
        }
        // 校验数据
        ThrowUtil.throwIf(StrUtil.isNotBlank(spaceName) && spaceName.length() > 20,
                ErrorCode.PARAMS_ERROR, "空间名字过长！");
        ThrowUtil.throwIf(spaceLevel != null && spaceLevelEnum == null, ErrorCode.PARAMS_ERROR,
                "空间等级输入有误！");
    }

    /**
     * 根据空间级别填充空间对象
     *
     * @param space 空间
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            // 当没有空间最大大小或者最大空间大小为0的时候设置最大空间大小
            if (space.getMaxSize() == null || space.getMaxSize() == 0) {
                space.setMaxSize(spaceLevelEnum.getMaxSize());
            }
            // 当没有空间最大数量或者最大空间数量为0的时候设置最大空间数量
            if (space.getMaxCount() == null || space.getMaxCount() == 0) {
                space.setMaxCount(spaceLevelEnum.getMaxCount());
            }
        }
    }
}




