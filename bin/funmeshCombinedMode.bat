@echo off
rem Changes working directory to the place this bat resides so it may be run from any place:
cd /d "%~dp0"

@echo on
@echo ========= Running funmesh in Combined Mode in a new window: start java -jar funmesh.jar %* roleId=all
@start "funmesh %* roleId=all: Combined Mode" java -jar funmesh.jar %* roleId=all"

