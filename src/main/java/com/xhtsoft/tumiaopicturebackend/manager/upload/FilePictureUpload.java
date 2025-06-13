package com.xhtsoft.tumiaopicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {

    /**
     * 获取文件原始名称
     *
     * @param inputSource 文件
     * @return 文件原始名称
     */
    @Override
    protected String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理上传的文件到本地
     *
     * @param inputSource 上传文件
     * @param file        本地文件
     * @throws Exception 异常
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }

    /**
     * 校验上传的文件
     *
     * @param inputSource 上传文件
     */
    @Override
    protected void validatePicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtil.throwIf(ObjUtil.isNull(multipartFile), ErrorCode.PARAMS_ERROR, "上传图片不能为空");
        // 校验文件大小
        final long MAXSIZE = 1024 * 1024 * 5;
        ThrowUtil.throwIf(multipartFile.getSize() > MAXSIZE, ErrorCode.PARAMS_ERROR, "上传图片大小不能超过5M");
        // 校验文件类型
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOWED_FILE_SUFFIX = Arrays.asList("png", "jpg", "jpeg", "webp");
        ThrowUtil.throwIf(!ALLOWED_FILE_SUFFIX.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "上传图片格式不正确");
    }
}
