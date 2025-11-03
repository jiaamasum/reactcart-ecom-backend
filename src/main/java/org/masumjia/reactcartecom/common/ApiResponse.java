package org.masumjia.reactcartecom.common;

public class ApiResponse<T> {
    private T data;
    private Object meta;
    private ApiError error;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> res = new ApiResponse<>();
        res.setData(data);
        return res;
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        ApiResponse<T> res = new ApiResponse<>();
        res.setData(data);
        res.setMeta(meta);
        return res;
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        ApiResponse<T> res = new ApiResponse<>();
        res.setError(error);
        return res;
    }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Object getMeta() { return meta; }
    public void setMeta(Object meta) { this.meta = meta; }
    public ApiError getError() { return error; }
    public void setError(ApiError error) { this.error = error; }
}

