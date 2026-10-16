@echo off
setlocal
cd /d "%~dp0"
set "JAR=target\pedidos-restaurante.jar"

if not exist "%JAR%" (
    echo.
    echo [ejecutar] No existe el ejecutable. Compilando sin internet...
    echo.
    call "%~dp0mvnw.cmd" -o -B -DskipTests package
    if errorlevel 1 (
        echo.
        echo [ejecutar] ERROR: no se pudo compilar. Revise los mensajes anteriores.
        exit /b 1
    )
)

echo.
echo [ejecutar] Iniciando pedidos-restaurante...
echo [ejecutar] Direccion:  http://localhost:8080
echo [ejecutar] Para detener: Ctrl+C
echo.
java -jar "%JAR%"
endlocal
