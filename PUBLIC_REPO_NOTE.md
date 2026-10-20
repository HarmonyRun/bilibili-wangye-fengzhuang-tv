# 公开仓库说明

本仓库是「哔哩客厅 / bilibili网页封装TV版」的源码公开快照。

未上传到公开仓库的内容：

- `qa/`：本地验收截图，包含真实账号头像/昵称/观看历史等个人信息；
- `design/`：设计参考与效果图素材（体积较大，且可能包含第三方图片）；
- `local.properties`、`app/build/`、`.gradle/`：本机 SDK 路径与构建缓存；
- `BiliLivingRoom-debug.apk`：构建产物。

完整的验收截图与备份保存在项目本机/备份归档中。需要构建时，请复制 `local.properties.example` 或自行创建 `local.properties` 并填写 Android SDK 路径。
