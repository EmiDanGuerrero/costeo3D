@echo off
setlocal
REM Costeo 3D - arranque sencillo sin Maven, sin empaquetado y sin EXE propio.
REM Guardar este archivo en la raiz del proyecto de Eclipse.
REM Requiere que Eclipse haya compilado las clases y que JavaFX SDK este instalado.
REM Si JavaFX SDK no esta junto al proyecto, definir JAVAFX_HOME apuntando a javafx-sdk-25.
REM El conector JDBC MySQL debe estar en lib\, libs\, drivers\ o COSTEO3D_JDBC_JAR.

cd /d "%~dp0"

set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

set "FX_LIB="
if defined JAVAFX_HOME (
    if exist "%JAVAFX_HOME%\lib\javafx.controls.jar" set "FX_LIB=%JAVAFX_HOME%\lib"
    if exist "%JAVAFX_HOME%\javafx.controls.jar" set "FX_LIB=%JAVAFX_HOME%"
)
if not defined FX_LIB if exist "%CD%\javafx-sdk-25.0.5\lib\javafx.controls.jar" set "FX_LIB=%CD%\javafx-sdk-25.0.5\lib"
if not defined FX_LIB if exist "%CD%\javafx\lib\javafx.controls.jar" set "FX_LIB=%CD%\javafx\lib"

if not defined FX_LIB (
    echo [ERROR] No encuentro la carpeta lib del SDK de JavaFX.
    echo Define JAVAFX_HOME con la ruta del SDK, por ejemplo C:\javafx-sdk-25.
    pause
    exit /b 1
)

set "CLASSES="
for %%P in ("bin" "bin\main" "out" "target\classes") do (
    if exist "%%~P\com\costeo3d\presentation\javafx\DesktopApplication.class" set "CLASSES=%%~P"
)
if not defined CLASSES (
    echo [ERROR] No encuentro DesktopApplication.class compilado.
    echo Compila el proyecto en Eclipse ^(Project ^> Build Project^) y vuelve a intentar.
    pause
    exit /b 1
)

REM El directorio del proyecto es el directorio de trabajo para config\ y logs\.
set "CP=%CLASSES%;src\main\resources;lib\*;libs\*;drivers\*"
if defined COSTEO3D_JDBC_JAR set "CP=%CP%;%COSTEO3D_JDBC_JAR%"

"%JAVA%" --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" com.costeo3d.presentation.javafx.DesktopApplication
if errorlevel 1 (
    echo.
    echo [ERROR] Costeo 3D no pudo iniciarse. Consulta el mensaje anterior.
    echo Comprueba Java, JavaFX SDK, el driver MySQL y config\database.local.properties.
    pause
    exit /b 1
)
endlocal
