# RenderDragonSourceCodeInv

Reverse render dragon shaders source code from .bin file

REBORN

## Building

Recommended to build on Windows, because bgfx shaderc does not support compiling Direct3D shaders on other platforms

### Windows

1. Run `setup_build_environment.bat`. This script will automatically download MaterialBinTool with shaderc and the data used to build materials. 
2. Run `build.bat` without arguments to build all materials for all platforms, or use arguments to specify which materials and platforms to build.   

The available arguments are:  
```
-t, --threads:     Specifies the number of threads to use for compilation. The default value is 1.
-p, --platform:    Specifies one or more target platforms. If not specified, build for all platforms.
-m, --material:    Specifies one or more materials to build. If not specified, build all materials.
--debug:           Enables debug information.
```
Examples:   

Build all materials for all platforms:   
```
./build.bat
```
Build RenderChunk and Sky for Windows and Android:   
```
./build.bat -p Windows Android -m RenderChunk Sky
```
