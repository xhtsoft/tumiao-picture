package com.xhtsoft.tumiaopicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhtsoft.tumiaopicturebackend.model.dto.picture.PictureUploadRequest;
import com.xhtsoft.tumiaopicturebackend.model.entity.Picture;
import com.xhtsoft.tumiaopicturebackend.model.entity.User;
import com.xhtsoft.tumiaopicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

/**
* @author xhtsoft
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-06-01 11:52:03
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);
}
