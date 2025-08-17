package com.xhtsoft.tumiaopicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhtsoft.tumiaopicturebackend.model.dto.space.analyze.*;
import com.xhtsoft.tumiaopicturebackend.model.entity.Space;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.vo.space.analyze.*;

import java.util.List;

/**
 * @author 51404
 * @createDate 2025-07-26 15:34:12
 */
public interface SpaceAnalyzeService extends IService<Space> {

    /**
     * 获取空间使用情况
     *
     * @param spaceUsageAnalyzeRequest 获取空间使用情况请求
     * @param loginUser                登录用户
     * @return 空间使用情况
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 获取空间分类情况
     *
     * @param spaceCategoryAnalyzeRequest 获取空间分类情况请求
     * @param loginUser                   登录用户
     * @return 空间分类情况
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间标签情况
     *
     * @param spaceTagAnalyzeRequest 获取空间标签情况请求
     * @param loginUser              登录用户
     * @return 空间标签情况
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间大小情况
     *
     * @param spaceSizeAnalyzeRequest 获取空间大小情况请求
     * @param loginUser               登录用户
     * @return 空间大小情况
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 获取空间用户情况
     *
     * @param spaceUserAnalyzeRequest 获取空间用户情况请求
     * @param loginUser               登录用户
     * @return 空间用户情况
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间排行情况
     *
     * @param spaceRankAnalyzeRequest 获取空间排行情况请求
     * @param loginUser               登录用户
     * @return 空间排行情况
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
