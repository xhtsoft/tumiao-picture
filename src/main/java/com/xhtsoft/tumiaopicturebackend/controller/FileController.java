package com.xhtsoft.tumiaopicturebackend.controller;

import com.xhtsoft.tumiaopicturebackend.common.BaseResponse;
import com.xhtsoft.tumiaopicturebackend.common.ResultUtils;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.manager.CosManger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private CosManger cosManger;

    @PostMapping("/test/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile) {
        String fileName = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName);
        File file = null;
        try {
            file = File.createTempFile(filePath, null);
            multipartFile.transferTo(file);
            cosManger.putObject(filePath, file);
            return ResultUtils.success(filePath);
        } catch (Exception e) {
            log.error("File upload error, filepath : {}", filePath);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            boolean deleted = file.delete();
            if (!deleted) {
                log.error("File delete error, filepath : {}", filePath);
            }
        }
    }
}
