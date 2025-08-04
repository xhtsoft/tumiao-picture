package com.xhtsoft.tumiaopicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {

    private static final long serialVersionUID = -3792462433441938738L;
    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件url
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间id
     */
    private Long spaceId;

}
