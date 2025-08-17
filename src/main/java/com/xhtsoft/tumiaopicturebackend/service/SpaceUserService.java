package com.xhtsoft.tumiaopicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.spaceUser.SpaceUserAddRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.spaceUser.SpaceUserQueryRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhtsoft.tumiaopicturebackend.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 51404
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2025-08-16 13:24:44
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 添加空间成员
     *
     * @param spaceUserAddRequest 添加空间成员请求
     * @return 添加的空间成员id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员对象
     *
     * @param spaceUser 空间成员对象
     * @param add       是否为创建
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取查询条件
     *
     * @param spaceUserQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 获取空间成员视图
     *
     * @param spaceUser 空间成员对象
     * @param request   请求
     * @return 空间成员视图
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员视图列表
     *
     * @param spaceUserList 空间成员列表
     * @return 空间成员视图列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
