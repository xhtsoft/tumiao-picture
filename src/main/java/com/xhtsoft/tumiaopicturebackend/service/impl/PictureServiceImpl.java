package com.xhtsoft.tumiaopicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.manager.upload.FilePictureUpload;
import com.xhtsoft.tumiaopicturebackend.manager.upload.PictureUploadTemplate;
import com.xhtsoft.tumiaopicturebackend.manager.upload.UrlPictureUpload;
import com.xhtsoft.tumiaopicturebackend.mapper.PictureMapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.file.UploadPictureResult;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.PictureQueryRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.PictureReviewRequest;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.PictureUploadRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.PictureReviewStatusEnum;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;
import com.xhtsoft.tumiaopicturebackend.model.vo.UserVO;
import com.xhtsoft.tumiaopicturebackend.service.PictureService;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 51404
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-06-01 11:52:03
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private UserService userService;

    /**
     * 上传图片
     *
     * @param inputSource          上传文件
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登录用户
     * @return 图片视图
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtil.throwIf(inputSource == null, ErrorCode.PARAMS_ERROR);
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtil.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        // 判断是新增还是更新
        Long pictureId = pictureUploadRequest.getId();
        if (pictureId != null && pictureId > 0) {
            // 更新
            Picture oldPicture = this.getById(pictureId);
            ThrowUtil.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑图片
            ThrowUtil.throwIf(!loginUser.getId().equals(oldPicture.getUserId()) && !userService.isAdmin(loginUser),
                    ErrorCode.NO_AUTH_ERROR);
        }
        // 上传图片
        // 按照用户id划分目录
        String uploadPicturePathPrefix = String.format("picture/%s", loginUser.getId());
        // 根据输入源的类型选择合适的上传方法
        PictureUploadTemplate  pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPicturePathPrefix);
        // 构造入库对象
        Picture picture = new Picture();
        picture.setName(uploadPictureResult.getPicName());
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // 操作数据库
        if (pictureId != null && pictureId > 0) {
            picture.setId(pictureId);
            picture.setUpdateTime(new Date());
        }
        // 插入数据库之前，补充审核参数
        this.fillReviewStatus(picture, loginUser);
        boolean result = this.saveOrUpdate(picture);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取pictureVO
     *
     * @param picture 图片
     * @param request http请求
     * @return pictureVO
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 获取分页数据
     *
     * @param picturePage picture分页数据
     * @param request     请求
     * @return VO分页数据
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 获取图片查询条件
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 图片查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        // 开始构造查询条件
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 校验数据
     *
     * @param picture 图片
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtil.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtil.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtil.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtil.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtil.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum value = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        ThrowUtil.throwIf(ObjUtil.isNull(id) || reviewStatus == null ||
                PictureReviewStatusEnum.REVIEWING.equals(value), ErrorCode.PARAMS_ERROR, "参数错误");
        // 2. 图片是否存在
        Picture picture = getById(pictureReviewRequest.getId());
        ThrowUtil.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 3. 图片审核状态是否重复
        ThrowUtil.throwIf(Objects.equals(picture.getReviewStatus(), pictureReviewRequest.getReviewStatus())
                , ErrorCode.OPERATION_ERROR, "图片审核操作重复");
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = updateById(updatePicture);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核状态
     *
     * @param picture   图片
     * @param loginUser 登录用户
     */
    @Override
    public void fillReviewStatus(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        } else {
            // 非管理员的创建和编辑图片操作都会使得图片编程待审核状态
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }
}



