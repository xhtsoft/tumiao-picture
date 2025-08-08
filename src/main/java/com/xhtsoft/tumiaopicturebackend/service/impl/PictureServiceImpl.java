package com.xhtsoft.tumiaopicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.AliyunAIApi;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.model.CreateOutPaintingTaskRequest;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.model.CreateOutPaintingTaskResponse;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.manager.CosManager;
import com.xhtsoft.tumiaopicturebackend.manager.upload.FilePictureUpload;
import com.xhtsoft.tumiaopicturebackend.manager.upload.PictureUploadTemplate;
import com.xhtsoft.tumiaopicturebackend.manager.upload.UrlPictureUpload;
import com.xhtsoft.tumiaopicturebackend.mapper.PictureMapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.file.UploadPictureResult;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.*;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.Space;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.PictureReviewStatusEnum;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;
import com.xhtsoft.tumiaopicturebackend.model.vo.UserVO;
import com.xhtsoft.tumiaopicturebackend.service.PictureService;
import com.xhtsoft.tumiaopicturebackend.service.SpaceService;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import com.xhtsoft.tumiaopicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 51404
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-06-01 11:52:03
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliyunAIApi aliyunAIApi;

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
        // 检验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtil.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验空间权限
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 校验空间额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间已满");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间已满");
            }
        }
        // 检验是否有空间的权限
        // 判断是新增还是更新
        Long pictureId = pictureUploadRequest.getId();
        if (pictureId != null && pictureId > 0) {
            // 更新
            Picture oldPicture = this.getById(pictureId);
            ThrowUtil.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑图片
            ThrowUtil.throwIf(!loginUser.getId().equals(oldPicture.getUserId()) && !userService.isAdmin(loginUser),
                    ErrorCode.NO_AUTH_ERROR);
            // 检验空间是否一致
            // 没传 spaceId,则复用原有图片的spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 用户传来的spaceId必须和原来的一致
                ThrowUtil.throwIf(!oldPicture.getSpaceId().equals(spaceId), ErrorCode.PARAMS_ERROR, "空间不一致");
            }
            // 清理旧图片
            clearPictureFile(oldPicture);
        }
        // 上传图片
        // 按照用户id划分目录
        String uploadPicturePathPrefix = null;
        if (spaceId == null) {
            // spaceId为空，则使用公共图库
            uploadPicturePathPrefix = String.format("picture/%s", loginUser.getId());
        } else {
            // 私有空间
            uploadPicturePathPrefix = String.format("picture/%s", spaceId);
        }
        // 根据输入源的类型选择合适的上传方法
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPicturePathPrefix);
        // 构造入库对象
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setName(uploadPictureResult.getPicName());
        // 如果用户自定义了图片名，则使用自定义的图片名
        if (StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picture.setName(pictureUploadRequest.getPicName());
        }
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setUserId(loginUser.getId());
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 操作数据库
        if (pictureId != null && pictureId > 0) {
            picture.setId(pictureId);
            picture.setUpdateTime(new Date());
        }
        // 插入数据库之前，补充审核参数
        this.fillReviewStatus(picture, loginUser);
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            // 更新空间额度
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtil.throwIf(!update, ErrorCode.OPERATION_ERROR, "上传图片时空间额度更新失败");
            }
            return picture;
        });
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        // 开始构造查询条件
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
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
        // >= 开始时间
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < 结束时间
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
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

    /**
     * 批量上传图片
     *
     * @param pictureUploadByBatchRequest 图片上传批量请求
     * @param loginUser                   登录用户
     * @return 图片数量
     */
    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验内容
        ThrowUtil.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        Integer searchNum = pictureUploadByBatchRequest.getCount();
        String searchText = pictureUploadByBatchRequest.getSearchText();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        // 名称前缀为空时，使用搜索内容作为名称前缀
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtil.throwIf(searchNum > 30, ErrorCode.PARAMS_ERROR, "最多抓取30张图片");
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (div == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        //Elements imgElementList = div.select("img.mimg");
        Elements imgElementList = div.select(".iusc");
        // 遍历元素，依次上传图片
        int uploadNum = 0;
        for (Element element : imgElementList) {
            // String fileUrl = element.attr("src");

            // 获取data-m属性中的JSON字符串
            String dataM = element.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片url）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片地址，防止转义或者和对象存储冲突的问题
            // 把Url中？以及？之后的字符去掉
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadNum + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功：id：{}", pictureVO.getId());
                uploadNum++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadNum >= searchNum) {
                break;
            }
        }
        return uploadNum;
    }

    /**
     * 清理图片文件
     *
     * @param oldPicture 旧图片
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String url = oldPicture.getUrl();
        Long count = this.lambdaQuery()
                .eq(Picture::getUrl, url)
                .count();
        // 不止一条记录在使用该图片
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(url);
        // 删除缩略图
        if (StrUtil.isNotBlank(oldPicture.getThumbnailUrl())) {
            cosManager.deleteObject(oldPicture.getThumbnailUrl());
        }
    }

    /**
     * 校验空间图片的权限
     *
     * @param loginUser 登录用户
     * @param picture   图片
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可见
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该图片");
            }
        } else {
            // 私有空间，仅该空间管理员可以操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该图片");
            }
        }
    }

    /**
     * 删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登录用户
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtil.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtil.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片删除失败");
            // 操作空间
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalNum = totalNum - 1")
                    .update();
            ThrowUtil.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间额度更新失败");
            return null;
        });
        // 操作数据库
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtil.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewStatus(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId   空间id
     * @param color     颜色
     * @param loginUser 登录用户
     * @return 图片视图列表
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String color, User loginUser) {
        // 1.校验参数
        ThrowUtil.throwIf(spaceId == null || StrUtil.isBlank(color), ErrorCode.PARAMS_ERROR, "参数错误");
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "无登录用户");
        // 2.校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtil.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtil.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        // 3.查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (pictureList.isEmpty()) {
            return new ArrayList<>();
        }
        // 4.将颜色字符串转换为主色调
        Color targetColor = Color.decode(color);
        // 5.计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparing(
                        picture -> {
                            String hexColor = picture.getPicColor();
                            // 没有主色调的图片会默认排序到最后
                            if (StrUtil.isBlank(hexColor)) return Double.MAX_VALUE;
                            Color pictureColor = Color.decode(hexColor);
                            return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                        }
                ))
                .limit(12)
                .collect(Collectors.toList());
        // 6.返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求
     * @param loginUser                 登录用户
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1.校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        if (pictureIdList == null || pictureIdList.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择图片");
        }
        ThrowUtil.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "请选择空间");
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "无登录用户");
        // 2.校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtil.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtil.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        // 3.查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4.更新分类和标签
        pictureList.forEach(picture -> {
            if (category != null) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5.操作数据库
        boolean result = this.updateBatchById(pictureList);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片批量更新操作失败");
    }

    /**
     * 创建图片外绘任务
     *
     * @param createPictureOutPaintingTaskRequest 图片外绘任务创建请求
     * @param loginUser                           登录用户
     * @return 创建的图片外绘任务响应
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        ThrowUtil.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR, "请选择图片");
        Picture picture = this.getById(pictureId);
        checkPictureAuth(loginUser, picture);
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliyunAIApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList 图片列表
     * @param nameRule    图片命名规则
     */

    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (nameRule == null || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                picture.setName(nameRule.replace("{序号}", String.valueOf(count)));
                count++;
            }
        } catch (Exception e) {
            log.error("名称解析错误");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}