package com.data.datafusion.util.entity;

public class StatusContent {

    public static final int FAIL_CODE = -1; //表示失败
    public static final int SUCCESS_CODE = 0; //正常且有数据
    public static final int REQUEST_INFO_INCORRECT = 1002; //请求信息不完整或不正确
    public static final int USER_AUTHORITY = 1003; //该用户未申请接口，无权限访问
    public static final int REQUEST_SQL_INCORRECT = 1004; //数据库查询出错
    public static final int REQUEST_PARAM_INCORRECT = 1005; //token不正确，请查验
    public static final int TABLE_INCORRECT = 1006; //数据表不存在，请查验
    public static final int PARAM_NOTSTANDARD = 1007; //数据编号不标准，必须是数字
    public static final int PARAM_NOTEXIST = 1008; //数据编号不存在
    public static final int REQUEST_INFO_NOTEMPTY = 1009; //请求信息不为空
    public static final int DATABASE_NOTEMPTY = 1010; //请填写数据源信息,databaseId或databaeName必填其一

    private Integer code;
    private String msg;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public static String getCodeMsg(Integer key) {
        switch (key) {
            case REQUEST_INFO_INCORRECT:
                return "请求参数不完整或不正确";
            case USER_AUTHORITY:
                return "用户未申请接口，无权限访问";
            case REQUEST_SQL_INCORRECT:
                return "数据库查询不正确";
            case TABLE_INCORRECT:
                return "数据表不存在";
            case PARAM_NOTSTANDARD:
                return "数据编号不标准，必须是数字";
            case PARAM_NOTEXIST:
                return "数据编号不存在";
            case REQUEST_PARAM_INCORRECT:
                return "token不存在";
            case REQUEST_TOKEN_INCORRECT:
                return "无权限使用，token不匹配";
            case REQUEST_INFO_NOTEMPTY:
                return "请求信息不为空";
            case DATABASE_NOTEMPTY:
                return "请填写数据源信息";
            case SUCCESS_CODE:
                return "成功";
            case FAIL_CODE:
                return "失败";
            default:
                return "未知错误";
        }
    }

    public static final int REQUEST_TOKEN_INCORRECT = 2005; //无权限使用，token不匹配
}
