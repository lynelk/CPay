@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ==============================================================
rem CPay build-and-deploy helper for a Windows build machine
rem Deploys the Spring Boot backend and frontend to a CentOS/Linux host
rem using SSH and the repository's deploy-server.sh script.
rem ==============================================================

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\.."
set "BACKEND_DIR=%PROJECT_ROOT%\Initializrspringbootprojectfresh"
set "FRONTEND_DIR=%PROJECT_ROOT%\Clientside"
set "REMOTE_SCRIPT=/tmp/deploy-server.sh"

rem ---- Configurable deployment values ----
if "%CPAY_DEPLOY_HOST%"=="" set "CPAY_DEPLOY_HOST=instance-dev"
if "%CPAY_DEPLOY_USER%"=="" set "CPAY_DEPLOY_USER=opc"
if "%CPAY_DEPLOY_PORT%"=="" set "CPAY_DEPLOY_PORT=22"
if "%CPAY_DEPLOY_SSH_KEY%"=="" set "CPAY_DEPLOY_SSH_KEY=%USERPROFILE%\.ssh\id_rsa"
if "%CPAY_BRANCH%"=="" set "CPAY_BRANCH=main"
if "%CPAY_REPO_URL%"=="" set "CPAY_REPO_URL=https://github.com/lynelk/CPay.git"
if "%CPAY_DOMAIN%"=="" set "CPAY_DOMAIN=cpay.coresynergi.es"

rem Optional override for production or sandbox deployment sites
if "%CPAY_APP_ROOT%"=="" set "CPAY_APP_ROOT=/opt/cpay"

set "BACKEND_JAR=%BACKEND_DIR%\target\cito-fresh-0.0.1-SNAPSHOT.jar"

call :log "Using project root: %PROJECT_ROOT%"
call :log "Deploy target: %CPAY_DEPLOY_USER%@%CPAY_DEPLOY_HOST%:%CPAY_DEPLOY_PORT%"

where mvn >nul 2>&1
if errorlevel 1 (
    call :error "Maven was not found in PATH. Install Maven 3.9+ and Java 21 first."
    exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
    call :error "npm was not found in PATH. Install Node.js 20+ and npm first."
    exit /b 1
)

where ssh >nul 2>&1
if errorlevel 1 (
    call :error "ssh was not found in PATH. Install an OpenSSH client first."
    exit /b 1
)

where scp >nul 2>&1
if errorlevel 1 (
    call :error "scp was not found in PATH. Install an OpenSSH client first."
    exit /b 1
)

if not exist "%BACKEND_DIR%\pom.xml" (
    call :error "Cannot find the backend Maven project in %BACKEND_DIR%."
    exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
    call :error "Cannot find the frontend project in %FRONTEND_DIR%."
    exit /b 1
)

if not exist "%CPAY_DEPLOY_SSH_KEY%" (
    call :error "SSH private key was not found at %CPAY_DEPLOY_SSH_KEY%."
    exit /b 1
)

call :log "Step 1/3: Building backend"
cd /d "%BACKEND_DIR%"
call mvn -DskipTests clean package
if errorlevel 1 (
    call :error "Backend Maven build failed."
    exit /b 1
)

if not exist "%BACKEND_JAR%" (
    call :error "Expected backend jar was not produced at %BACKEND_JAR%."
    exit /b 1
)

call :log "Step 2/3: Building frontend"
cd /d "%FRONTEND_DIR%"
call npm install
if errorlevel 1 (
    call :error "npm install failed for the frontend."
    exit /b 1
)

call npm run build
if errorlevel 1 (
    call :error "Frontend production build failed."
    exit /b 1
)

call :log "Step 3/3: Deploying to the CentOS target"
call scp -i "%CPAY_DEPLOY_SSH_KEY%" -P "%CPAY_DEPLOY_PORT%" -o StrictHostKeyChecking=no "%SCRIPT_DIR%deploy-server.sh" "%CPAY_DEPLOY_USER%@%CPAY_DEPLOY_HOST%:%REMOTE_SCRIPT%"
if errorlevel 1 (
    call :error "Unable to upload the deployment script to the remote host."
    exit /b 1
)

rem The remote deployment script already contains the production deployment logic.
rem The variables below are passed through so the same script can build and
rem install the application on the Linux server.
call ssh -i "%CPAY_DEPLOY_SSH_KEY%" -p "%CPAY_DEPLOY_PORT%" -o StrictHostKeyChecking=no "%CPAY_DEPLOY_USER%@%CPAY_DEPLOY_HOST%" "sudo chmod +x %REMOTE_SCRIPT% && sudo CPAY_REPO_URL='%CPAY_REPO_URL%' CPAY_BRANCH='%CPAY_BRANCH%' CPAY_DOMAIN='%CPAY_DOMAIN%' CPAY_APP_ROOT='%CPAY_APP_ROOT%' bash %REMOTE_SCRIPT%"
if errorlevel 1 (
    call :error "Remote deployment failed on the CentOS server."
    exit /b 1
)

call :log "Deployment finished successfully."
exit /b 0

:log
echo [cpay-build-deploy] %~1
exit /b 0

:error
echo [cpay-build-deploy][ERROR] %~1
exit /b 0
