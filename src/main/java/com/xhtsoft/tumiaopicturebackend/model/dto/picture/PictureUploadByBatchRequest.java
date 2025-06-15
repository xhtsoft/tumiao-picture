package com.xhtsoft.tumiaopicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量上传图片请求
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    private static final long serialVersionUID = 9092573651120815649L;
    /**
     * 抓取搜索词
     */
    private String searchText;

    /**
     * 默认抓取数量为10
     */
    private Integer count = 10;

    /**
     * 图片名称前缀
     */
    private String namePrefix;

}
