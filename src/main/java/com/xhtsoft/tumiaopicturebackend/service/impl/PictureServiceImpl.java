package com.xhtsoft.tumiaopicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import com.xhtsoft.tumiaopicturebackend.manager.FileManager;
import com.xhtsoft.tumiaopicturebackend.mapper.PictureMapper;
import com.xhtsoft.tumiaopicturebackend.model.dto.file.UploadPictureResult;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.PictureUploadRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;
import com.xhtsoft.tumiaopicturebackend.service.PictureService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;

/**
 * @author 51404
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-06-01 11:52:03
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    /**
     * 上传图片
     *
     * @param multipartFile        文件
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登录用户
     * @return 图片视图
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtil.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR);
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtil.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        // 判断是新增还是删除
        Long pictureId = pictureUploadRequest.getId();
        if (pictureId != null && pictureId > 0) {
            // 更新
            boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId).exists();
            ThrowUtil.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 上传图片
        // 按照用户id划分目录
        String uploadPicturePathPrefix = String.format("picture/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPicturePathPrefix);
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
        if(pictureId != null && pictureId > 0){
            picture.setId(pictureId);
            picture.setUpdateTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objToVo(picture);
    }

}




