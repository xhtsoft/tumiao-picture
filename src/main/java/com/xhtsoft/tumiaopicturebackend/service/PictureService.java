package com.xhtsoft.tumiaopicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.*;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author xhtsoft
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-06-01 11:52:03
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param inputSource          上传的文件源
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登录用户
     * @return 图片视图
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取pictureVO
     *
     * @param picture 图片
     * @param request http请求
     * @return pictureVO
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取分页数据
     *
     * @param picturePage picture分页数据
     * @param request     请求
     * @return VO分页数据
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 获取图片查询条件
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 图片查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 校验数据
     *
     * @param picture 图片
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核状态
     *
     * @param picture   图片
     * @param loginUser 登录用户
     */
    void fillReviewStatus(Picture picture, User loginUser);

    /**
     * 批量上传图片
     *
     * @param pictureUploadByBatchRequest 图片上传批量请求
     * @param loginUser                   登录用户
     * @return 图片数量
     */
    int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                             User loginUser);

    /**
     * 清理图片文件
     *
     * @param oldPicture 旧图片
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 校验空间图片的权限
     *
     * @param loginUser 登录用户
     * @param picture   图片
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登录用户
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);
}
