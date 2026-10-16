@echo off
setlocal
cd /d "%~dp0"
echo [compilar] Compilando y empaquetando SIN INTERNET (offline)...
echo.
call "%~dp0mvnw.cmd" -o -B package
if errorlevel 1 (
    echo.
    echo [compilar] ERROR: la compilacion fallo. Revise los mensajes anteriores.
    exit /b 1
)
echo.
echo [compilar] Listo. Ejecutable: target\pedidos-restaurante.jar
echo [compilar] Ejecutelo con:  java -jar target\pedidos-restaurante.jar  (o ejecutar.cmd)
endlocal
