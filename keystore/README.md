# Release 签名

`release.jks` 只放本机，不要提交。

密码和路径写在仓库根目录 `local.properties`：

```
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=renovation-ledger
RELEASE_KEY_PASSWORD=...
```

微信开放平台「应用签名」填证书 MD5（小写、无冒号），见本机生成的 `wechat-signature.txt`。
