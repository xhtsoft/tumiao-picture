package com.xhtsoft.tumiaopicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.xhtsoft.tumiaopicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片上传公共操作
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传图片(返回图片信息)
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 创建新的对图片处理对象
        PicOperations picOperations = new PicOperations();
        // 图片处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 设置图片处理规则为获取图片信息
        picOperations.setIsPicInfo(1);
        // 图片压缩（转换为Webp格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule rule = new PicOperations.Rule();
        rule.setFileId(webpKey);
        rule.setRule("imageMogr2/format/webp");
        rule.setBucket(cosClientConfig.getBucket());
        rules.add(rule);
        // 缩略图处理，仅对图片大小大于20KB的进行处理
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            // 拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail" + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s", 400, 400));
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            rules.add(thumbnailRule);
        }
        // 对上传请求设置图片处理规则
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 唯一键
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}
