@echo off

set MBT_VERSION=0.8.2
set DATA_VERSION=1.20.60

set MBT_DIR=env\MaterialBinTool-%MBT_VERSION%
set MBT=%MBT_DIR%\MaterialBinTool-%MBT_VERSION%-native-image.exe
set SHADERC=%MBT_DIR%\shaderc.exe

set DATA_DIR=data\%DATA_VERSION%

cd %~dp0

if not exist env (
    mkdir env
)
if not exist %MBT_DIR% (
    mkdir %MBT_DIR%
)
if not exist %MBT% (
    echo Downloading MaterialBinTool-%MBT_VERSION%
    curl -L -o %MBT% https://github.com/ddf8196/MaterialBinTool/releases/download/v%MBT_VERSION%/MaterialBinTool-%MBT_VERSION%-native-image.exe
)
if not exist %SHADERC% (
    echo Downloading shaderc
    curl -L -o %SHADERC% https://github.com/ddf8196/MaterialBinTool/releases/download/v%MBT_VERSION%/shaderc.exe
)

if not exist data (
    echo Cloning RenderDragonData
    git clone --branch %DATA_VERSION% --single-branch --depth 1 --origin origin https://github.com/ddf8196/RenderDragonData.git data
)
if not exist %DATA_DIR% (
    echo Updating RenderDragonData
    pushd data
    git fetch origin %DATA_VERSION%:%DATA_VERSION%
    git checkout %DATA_VERSION%
    git checkout .
    git pull
    popd
)
