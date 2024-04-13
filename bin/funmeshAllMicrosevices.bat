@echo off
rem Changes working directory to the place this bat resides so it may be run from any place:
cd /d "%~dp0"

@echo on
@echo ====== Running all funmesh Microservices
@FOR /L %%r IN (16, -1, 1) DO @CALL funmeshnw.bat %* roleId=%%r
