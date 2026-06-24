export const STATUS_CODE = {
    SUCCESS: 200,
    BAD_REQUEST: 400,
    UNAUTHORIZED: 401,
    FORBIDDEN: 403,
    NOT_FOUND: 404,
    CONFLICT: 409,
    INTERNAL_SERVER_ERROR: 500,
};

export const STATUS_CODE_MESSAGE: Record<number, string> = {
    [STATUS_CODE.SUCCESS]: '成功',
    [STATUS_CODE.BAD_REQUEST]: '请求参数错误',
    [STATUS_CODE.UNAUTHORIZED]: '未认证 / Token 过期',
    [STATUS_CODE.FORBIDDEN]: '无权限',
    [STATUS_CODE.NOT_FOUND]: '资源不存在',
    [STATUS_CODE.CONFLICT]: '业务冲突',
    [STATUS_CODE.INTERNAL_SERVER_ERROR]: '服务器内部错误',
};

