# 发布到 Maven Central

TokenFamily SDK 使用 `maven-publish`、Gradle Signing Plugin 和 `gradle-nexus/publish-plugin`，通过 Central Portal 的 OSSRH Staging API 兼容服务发布。

## 凭据

不要把凭据或私钥提交到仓库。开发机可写入用户目录下的 `~/.gradle/gradle.properties`：

```properties
mavenCentralUsername=<Central Portal Token Username>
mavenCentralPassword=<Central Portal Token Password>
signingKey=<ASCII-armored private key，换行写成 \n>
signingPassword=<私钥口令；无口令时留空>
```

CI 可使用等价环境变量：

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

公钥必须发布到 Maven Central 能访问的 OpenPGP keyserver，例如 `keyserver.ubuntu.com`。

## 本地验证

以下命令只生成并签名本地产物，不会上传：

```bash
./gradlew checkMavenCentralCredentials
./gradlew :sdk:publishReleasePublicationToMavenLocal
```

检查 `~/.m2/repository/top/ntutn/tokenfamily-sdk/<version>/`，应包含 AAR、POM、sources、javadoc 及各自的 `.asc` 签名。

## 发布

先确认 `sdk/build.gradle.kts` 中的版本尚未在 Maven Central 使用。Central 发布版本不可覆盖。

上传、关闭并自动发布：

```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

若希望先在 Central Publisher Portal 中人工检查，只执行：

```bash
./gradlew publishToSonatype closeSonatypeStagingRepository
```

关闭 staging repository 后，部署会出现在 [Central Publisher Portal](https://central.sonatype.com/publishing)。
