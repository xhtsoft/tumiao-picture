package com.xhtsoft.tumiaopicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhtsoft.tumiaopicturebackend.model.dto.space.SpaceAddRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.space.SpaceQueryRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.Space;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 51404
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-07-26 15:34:12
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     *
     * @param spaceAddRequest 创建空间请求
     * @param loginUser       请求用户
     * @return 空间id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 获取空间包装类（单条）
     *
     * @param space   空间
     * @param request http请求
     * @return spaceVO
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage space分页数据
     * @param request   请求
     * @return VO分页数据
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取空间查询条件
     *
     * @param spaceQueryRequest 空间查询请求
     * @return 空间查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 校验空间
     *
     * @param space 空间
     * @param add   是否为创建
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间级别填充空间对象
     *
     * @param space 空间
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 检查空间权限
     *
     * @param loginUser 登录用户
     * @param space     空间
     */
    void checkSpaceAuth(User loginUser, Space space);
}
