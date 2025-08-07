package com.xhtsoft.tumiaopicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 颜色搜图dto
 */
@Data
public class SearchPictureByColorRequest implements Serializable {

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
