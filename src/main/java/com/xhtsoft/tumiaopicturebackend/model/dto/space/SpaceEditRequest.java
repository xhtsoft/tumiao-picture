package com.xhtsoft.tumiaopicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑空间请求(普通用户使用)
 */
@Data
public class SpaceEditRequest implements Serializable {

    private static final long serialVersionUID = -6116008769647032491L;
    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

}
