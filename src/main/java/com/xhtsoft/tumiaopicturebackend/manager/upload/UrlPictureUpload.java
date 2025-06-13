package com.xhtsoft.tumiaopicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Url上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    /**
     * 获取文件原始名称
     *
     * @param inputSource 文件
     * @return 文件原始名称
     */
    @Override
    protected String getOriginalFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    /**
     * 下载文件到本地
     *
     * @param inputSource 上传文件
     * @param file        本地文件
     * @throws Exception 异常
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }

    /**
     * 校验图片Url
     *
     * @param inputSource 上传文件
     */
    @Override
    protected void validatePicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 校验非空
        ThrowUtil.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "上传图片不能为空");
        // 校验url格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传图片url格式错误");
        }
        // 校验url的协议
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传图片url格式错误");
        }
        // 发送 HEAD 请求验证图片是否存在
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 文件存在，文件类型校验
            String contentType = httpResponse.header("Content-Type");
            if (!StrUtil.isBlank(contentType)) {
                final List<String> ALLOWED_FILE_SUFFIX = Arrays.asList("image/png", "image/jpg", "image/jpeg", "image/webp");
                ThrowUtil.throwIf(!ALLOWED_FILE_SUFFIX.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "上传图片格式不正确");
            }
            // 文件大小校验
            String contentLength = httpResponse.header("Content-Length");
            final long MAXSIZE = 1024 * 1024 * 5;
            if (StrUtil.isNotBlank(contentLength)) {
                try {
                    ThrowUtil.throwIf(Long.parseLong(contentLength) > MAXSIZE,
                            ErrorCode.PARAMS_ERROR, "上传图片大小不能超过5M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传图片大小错误");
                }
            }
        } finally {
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }
}
