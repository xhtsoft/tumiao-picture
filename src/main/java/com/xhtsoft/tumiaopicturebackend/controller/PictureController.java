package com.xhtsoft.tumiaopicturebackend.controller;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xhtsoft.tumiaopicturebackend.annotation.AuthCheck;
import com.xhtsoft.tumiaopicturebackend.api.imagesearch.ImageSearchApiFacade;
import com.xhtsoft.tumiaopicturebackend.api.imagesearch.model.ImageSearchResult;
import com.xhtsoft.tumiaopicturebackend.common.BaseResponse;
import com.xhtsoft.tumiaopicturebackend.common.DeleteRequest;
import com.xhtsoft.tumiaopicturebackend.common.ResultUtils;
import com.xhtsoft.tumiaopicturebackend.constant.UserConstant;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.*;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.Space;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.enums.PictureReviewStatusEnum;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureTagCategory;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;
import com.xhtsoft.tumiaopicturebackend.service.PictureService;
import com.xhtsoft.tumiaopicturebackend.service.SpaceService;
import com.xhtsoft.tumiaopicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceService spaceService;

    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L) // 最大缓存数量为10000
                    .expireAfterWrite(5L, TimeUnit.MINUTES)// 缓存 5 分钟移除
                    .build();


    /**
     * 上传图片
     *
     * @param multipartFile        文件
     * @param pictureUploadRequest 请求
     * @param request              http请求
     * @return 图片信息
     */
    @PostMapping("/upload")
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过URL上传图片
     *
     * @param pictureUploadRequest 请求
     * @param request              http请求
     * @return 图片信息
     */
    @PostMapping("/upload/url")
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     *
     * @param deleteRequest 删除请求
     * @return 是否成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
            , HttpServletRequest request) {
        ThrowUtil.throwIf(ObjUtil.isNull(deleteRequest), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片
     *
     * @param pictureUpdateRequest 图片更新请求
     * @param request              http请求
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtil.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 插入数据库之前，补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewStatus(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据id获取图片（仅管理员）
     *
     * @param id      图片id
     * @param request http请求
     * @return 图片信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtil.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtil.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据id获取图片VO
     *
     * @param id      图片id
     * @param request http请求
     * @return 图片VO
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtil.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtil.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User LoginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(LoginUser, picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员）
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 图片分页列表
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装）
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             http请求
     * @return 图片VO分页列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtil.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公共图库
            // 设置用户仅能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtil.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装,有缓存）
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             http请求
     * @return 图片VO分页列表
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtil.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 设置用户仅能看到审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 先查询缓存，缓存中没有再去查询数据库
        // 构建缓存的 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = String.format("tumiao:listPictureVOByPage:%s", hashKey);
        // 构建Caffeine缓存的key
        String caffeineKey = String.format("listPictureVOByPage:%s", hashKey);
        String caffeineValue = LOCAL_CACHE.getIfPresent(caffeineKey);
        if (caffeineValue != null) {
            return ResultUtils.success(JSONUtil.toBean(caffeineValue, Page.class));
        }
        // 操作Redis，从缓存中查询
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        String cachedValue = opsForValue.get(redisKey);
        if (cachedValue != null) {
            // 缓存命中，更新本地缓存
            LOCAL_CACHE.put(caffeineKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 设置缓存的值
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 存入本地缓存
        LOCAL_CACHE.put(caffeineKey, cacheValue);
        // 设置缓存过期时间，5 - 10分钟过期，防止缓存雪崩，存入Redis
        int expireTime = RandomUtil.randomInt(5 * 60, 10 * 60);
        opsForValue.set(redisKey, cacheValue, expireTime, TimeUnit.SECONDS);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（用户）
     *
     * @param pictureEditRequest 图片编辑请求
     * @param request            http请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        pictureService.editPicture(pictureEditRequest, userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    /**
     * 预置标签和分类
     *
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "二次元", "高清", "动漫", "游戏", "创意", "唯美", "风景", "科幻");
        List<String> categoryList = Arrays.asList("壁纸", "头像", "表情包", "素材", "海报", "模板");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核（管理员）
     *
     * @param pictureReviewRequest 图片审核请求
     * @param request              http请求
     * @return 是否成功
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        // 数据校验
        ThrowUtil.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量上传图片
     *
     * @param pictureUploadByBatchRequest 批量上传请求
     * @param request                     http请求
     * @return 上传成功的条数
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        // 数据校验
        ThrowUtil.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 图片搜索（图片）
     *
     * @param searchPictureByPictureRequest 图片搜索请求
     * @return 图片搜索结果
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtil.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtil.throwIf(pictureId == null && pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtil.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.searchImage(picture.getUrl());
        return ResultUtils.success(imageSearchResults);
    }

    /**
     * 图片搜索（颜色）
     *
     * @param searchPictureByColorRequest 图片搜索请求
     * @param request                     http请求
     * @return 图片搜索结果
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest
            , HttpServletRequest request) {
        ThrowUtil.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, userService.getLoginUser(request));
        return ResultUtils.success(pictureVOList);
    }

    /**
     * 图片批量编辑
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求
     * @param request                   http请求
     * @return 是否成功
     */
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        // 数据校验
        ThrowUtil.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }
}