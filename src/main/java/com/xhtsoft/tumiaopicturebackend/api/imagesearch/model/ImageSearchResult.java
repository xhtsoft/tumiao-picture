package com.xhtsoft.tumiaopicturebackend.api.imagesearch.model;

import lombok.Data;

/**
 * 以图搜图结果
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}
