package com.xhtsoft.tumiaopicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.xhtsoft.tumiaopicturebackend.config.CosClientConfig;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     *
     * @param multipartFile    文件
     * @param uploadPathPrefix 文件上传路径前缀
     * @return 文件上传结果
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validatePicture(multipartFile);
        // 文件上传地址
        String uuid = RandomUtil.randomString(16);
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        String date = DateUtil.formatDate(new Date());
        String uploadFileName = String.format("%s_%s.%s", date, uuid, suffix);
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            double picScale = NumberUtil.round((double) width / height, 2).doubleValue();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setPicName(FileUtil.mainName(multipartFile.getOriginalFilename()));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(width);
            uploadPictureResult.setPicHeight(height);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            // 临时文件清理
            deleteTempFile(file);
        }
        // 临时文件清理
    }

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

    /**
     * 校验文件
     *
     * @param multipartFile 文件
     * @return 文件校验结果
     */
    private boolean validatePicture(MultipartFile multipartFile) {
        ThrowUtil.throwIf(ObjUtil.isNull(multipartFile), ErrorCode.PARAMS_ERROR, "上传图片不能为空");
        // 校验文件大小
        final long MAXSIZE = 1024 * 1024 * 5;
        ThrowUtil.throwIf(multipartFile.getSize() > MAXSIZE, ErrorCode.PARAMS_ERROR, "上传图片大小不能超过5M");
        // 校验文件类型
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOWED_FILE_SUFFIX = Arrays.asList("png", "jpg", "jpeg", "webp");
        ThrowUtil.throwIf(!ALLOWED_FILE_SUFFIX.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "上传图片格式不正确");
        return true;
    }
}
