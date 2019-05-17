@REM Phoebus launcher for Windows
@REM Uses a JDK that's located next to this folder,
@REM otherwise assumes JDK is on the PATH

@cd %~P0

@IF EXIST "%~P0%..\jdk" (
    set JAVA_HOME=%~P0%..\jdk
    @path %JAVA_HOME%\bin
    @ECHO Found JDK %JAVA_HOME%
)

if EXIST "update" (
    @ECHO Installing update...
    @rd /S/Q doc
    @rd /S/Q lib
    @move /Y update\*.* .
    @move /Y update\doc .
    @move /Y update\lib .
    @rmdir update
    @ECHO Updated.
)

@set V=0.0.1

@IF EXIST product-%V%.jar (
  SET JAR=product-%V%.jar
) ELSE (
  SET JAR=product-%V%-SNAPSHOT.jar

)

@java -jar %JAR% %*

