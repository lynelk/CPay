# CPay Admin Backend (Fresh)

Spring Boot 4.1.0 · Java 21+ · MySQL 8

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 or 22 | Java 17 on your system PATH will **not** work — it cannot compile Java 21 targets |
| Maven | 3.8+ | Bundled with IntelliJ or installed separately |
| MySQL | 8.x | Database `cpayadmin` must exist |

## First-time setup

### 1. Create the database

```sql
CREATE DATABASE IF NOT EXISTS cpayadmin CHARACTER SET utf8mb3;
```

### 2. Create `application-local.properties`

Create the file at:
```
src/main/resources/application-local.properties
```

Minimum contents (update credentials to match your local MySQL):

```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/cpayadmin?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# Actuator / admin API
actuator.username=actuator
actuator.password=actuator123
admin.api.username=apiadmin
admin.api.password=apiadmin123

# Signing secrets (any non-blank value for local dev)
callback.signing.secret=local-dev-callback-secret-changeme
merchant.channel.encryption.key=local-dev-encrypt-key-changeme-32

# Mail (dummy values — app will start but won't send real emails)
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=dev@local
spring.mail.password=dev

# Disable devtools restart
spring.devtools.restart.enabled=false
spring.devtools.livereload.enabled=false
```

> `application-local.properties` is gitignored — never commit it.

Flyway will automatically create all tables on first startup.

---

## Running the backend

> **Important:** If your system `PATH` defaults to Java 17, you must point to Java 21/22 explicitly, otherwise Maven will fail with `release version 21 not supported`.

### PowerShell

```powershell
$env:JAVA_HOME = "D:\joe\Software\java\openlogic-openjdk-22.0.2+9-windows-x64\openlogic-openjdk-22.0.2+9-windows-x64"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd "D:\joe\Jose\projects\joeWork\cito\cpay\newcpay\CPay\InitializrSpringbootProjectFresh"
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### Command Prompt

```cmd
set JAVA_HOME=D:\joe\Software\java\openlogic-openjdk-22.0.2+9-windows-x64\openlogic-openjdk-22.0.2+9-windows-x64
set PATH=%JAVA_HOME%\bin;%PATH%
cd D:\joe\Jose\projects\joeWork\cito\cpay\newcpay\CPay\InitializrSpringbootProjectFresh
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### Git Bash

```bash
export JAVA_HOME="D:/joe/Software/java/openlogic-openjdk-22.0.2+9-windows-x64/openlogic-openjdk-22.0.2+9-windows-x64"
export PATH="$JAVA_HOME/bin:$PATH"
cd "D:/joe/Jose/projects/joeWork/cito/cpay/newcpay/CPay/InitializrSpringbootProjectFresh"
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### IntelliJ IDEA (recommended)

1. **Run → Edit Configurations → `+` → Application**
2. Set **Main class**: `net.citotech.cito.CpayadminFreshApplication`
3. Set **VM options**: `-Dspring.profiles.active=local`
4. Set **JRE**: point to your Java 21/22 installation
5. Click **Run**

All startup logs appear unfiltered in the IntelliJ console with this approach.

---

## Stopping the backend

- **Terminal**: `Ctrl + C`
- **IntelliJ**: click the red Stop button

---

## Verify it is running

```
GET http://localhost:8081/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

The app starts on port **8081** by default. Override with env var `HTTP_PORT`.
