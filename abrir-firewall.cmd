@echo off
setlocal
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando permisos de administrador para abrir el puerto...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo Abriendo el puerto 8080 (TCP) en el Firewall de Windows...
netsh advfirewall firewall delete rule name="Pedidos 8080" >nul 2>&1
netsh advfirewall firewall add rule name="Pedidos 8080" dir=in action=allow protocol=TCP localport=8080 profile=any

echo.
echo Listo. Otros dispositivos de la misma red Wi-Fi ya pueden entrar.
echo Averigue la IP de este PC con:  ipconfig
echo y abra en el celular/tablet/otro PC:  http://IP-DE-ESTE-PC:8080
echo.
pause
endlocal
