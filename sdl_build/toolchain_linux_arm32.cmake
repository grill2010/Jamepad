# 32-bit ARM (hard float) Linux cross build.
# Requires gcc-arm-linux-gnueabihf / g++-arm-linux-gnueabihf, or the versioned
# gcc-<n>-arm-linux-gnueabihf packages those metapackages point at.

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR armv7l)

set(TARGET_TRIPLET arm-linux-gnueabihf)

# The unversioned names come from gcc-defaults; falling back to the versioned ones
# means the build also works when only the gcc-<n>-cross packages are installed.
find_program(CMAKE_C_COMPILER
        NAMES ${TARGET_TRIPLET}-gcc ${TARGET_TRIPLET}-gcc-12 ${TARGET_TRIPLET}-gcc-11 ${TARGET_TRIPLET}-gcc-10 REQUIRED)
find_program(CMAKE_CXX_COMPILER
        NAMES ${TARGET_TRIPLET}-g++ ${TARGET_TRIPLET}-g++-12 ${TARGET_TRIPLET}-g++-11 ${TARGET_TRIPLET}-g++-10 REQUIRED)

# Pinned explicitly because CMake otherwise derives them from the compiler's name,
# which does not give the right prefix for a versioned compiler.
find_program(CMAKE_AR NAMES ${TARGET_TRIPLET}-ar REQUIRED)
find_program(CMAKE_RANLIB NAMES ${TARGET_TRIPLET}-ranlib REQUIRED)
find_program(CMAKE_STRIP NAMES ${TARGET_TRIPLET}-strip)

set(CMAKE_LIBRARY_ARCHITECTURE ${TARGET_TRIPLET})
set(CMAKE_FIND_ROOT_PATH /usr/${TARGET_TRIPLET} /usr/lib/${TARGET_TRIPLET})

# Look for programs on the host, but headers and libraries in the target sysroot
# plus the Debian multiarch directories.
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

# Prefer the multiarch-aware pkg-config wrapper when the cross packages provide one.
find_program(PKG_CONFIG_EXECUTABLE NAMES ${TARGET_TRIPLET}-pkg-config pkg-config)
