package com.xhtsoft.tumiaopicturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.xhtsoft.tumiaopicturebackend.config.CosClientConfig;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.manager.CosManager;
import com.xhtsoft.tumiaopicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     *
     * @param inputSource      文件
     * @param uploadPathPrefix 文件上传路径前缀
     * @return 文件上传结果
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验图片
        validatePicture(inputSource);
        // 文件上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginalFilename(inputSource);
        String suffix = FileUtil.getSuffix(originalFilename);
        String date = DateUtil.formatDate(new Date());
        String uploadFileName = String.format("%s_%s.%s", date, uuid, suffix);
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(uploadPath, null);
            processFile(inputSource, file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            return buildResult(imageInfo, uploadPath, originalFilename, file);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            // 临时文件清理
            deleteTempFile(file);
        }
    }

    /**
     * 构建上传结果
     *
     * @param imageInfo        图片信息
     * @param uploadPath       上传路径
     * @param originalFilename 原始文件名
     * @param file             文件
     * @return 上传结果
     */
    private UploadPictureResult buildResult(ImageInfo imageInfo, String uploadPath, String originalFilename, File file) {
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        double picScale = NumberUtil.round((double) width / height, 2).doubleValue();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        return uploadPictureResult;
    }

    /**
     * 获取原始文件名
     *
     * @param inputSource 文件
     * @return 文件名
     */
    protected abstract String getOriginalFilename(Object inputSource);

    /**
     * 处理文件
     *
     * @param inputSource 文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 校验图片
     *
     * @param inputSource 文件
     */
    protected abstract void validatePicture(Object inputSource);

    /**
     * 清理临时文件
     *
     * @param file 文件
     */
    public static void deleteTempFile(File file) {
        if (file == null) return;
        boolean deletedResult = file.delete();
        if (!deletedResult) {
            log.error("File delete error, filepath : {}", file.getAbsoluteFile());
        }
    }
}
