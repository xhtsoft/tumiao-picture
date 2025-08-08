package com.xhtsoft.tumiaopicturebackend.api.aliyunAI;

import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.model.CreateOutPaintingTaskRequest;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.model.CreateOutPaintingTaskResponse;
import com.xhtsoft.tumiaopicturebackend.api.aliyunAI.model.GetOutPaintingTaskResponse;
import com.xhtsoft.tumiaopicturebackend.exception.BusinessException;
import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import com.xhtsoft.tumiaopicturebackend.exception.ThrowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliyunAIApi {

    // 读取配置文件
    @Value("${aliyunAI.apiKey}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest 创建任务请求参数
     * @return 创建任务结果
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtil.throwIf(createOutPaintingTaskRequest == null, ErrorCode.PARAMS_ERROR, "扩图参数为空");
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                // 必须开启异步处理，设置为enable。
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("创建任务失败：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI扩图失败！");
            }
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (createOutPaintingTaskResponse.getCode() != null) {
                log.error("请求异常：{}", createOutPaintingTaskResponse.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI扩图失败！" + createOutPaintingTaskResponse.getMessage());
            }
            return createOutPaintingTaskResponse;
        }
    }


    /**
     * 查询任务执行结果
     *
     * @param taskId 任务ID
     * @return 任务状态
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        ThrowUtil.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务ID为空");
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
