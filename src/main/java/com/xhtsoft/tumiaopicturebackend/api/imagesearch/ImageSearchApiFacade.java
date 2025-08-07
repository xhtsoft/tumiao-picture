package com.xhtsoft.tumiaopicturebackend.api.imagesearch;

import com.xhtsoft.tumiaopicturebackend.api.imagesearch.model.ImageSearchResult;
import com.xhtsoft.tumiaopicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.xhtsoft.tumiaopicturebackend.api.imagesearch.sub.GetImageListApi;
import com.xhtsoft.tumiaopicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 组合以图搜图3步（门面模式）
 */
@Slf4j
public class ImageSearchApiFacade {
    /**
     * 搜索图片
     *
     * @param imageUrl 图片url
     * @return 图片列表
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        return GetImageListApi.getImageList(imageFirstUrl);
    }
}
