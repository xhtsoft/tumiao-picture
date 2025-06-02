package com.xhtsoft.tumiaopicturebackend.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> TagList;

    /**
     * 分类列表
     */
    private List<String> CategoryList;
}
