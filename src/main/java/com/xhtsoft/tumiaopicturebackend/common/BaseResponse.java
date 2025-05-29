package com.xhtsoft.tumiaopicturebackend.common;

import com.xhtsoft.tumiaopicturebackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 响应类
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public BaseResponse(int code, T data) {
        this(code, "", data);
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
