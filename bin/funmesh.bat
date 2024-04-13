@echo off

rem Changes working directory to the place this bat resides so it may be run from any place:
cd /d "%~dp0"
@echo on
@echo === Running funmesh here: java -jar funmesh.jar %*
@java -jar funmesh.jar %*

