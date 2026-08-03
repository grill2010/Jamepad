# 32-bit x86 Linux build using the host multilib toolchain (gcc-multilib / g++-multilib).
# Deliberately does not set CMAKE_SYSTEM_NAME: this is a same-OS multilib build, not a
# cross build, so CMake should keep using the host's find/pkg-config behaviour.

set(CMAKE_SYSTEM_PROCESSOR i686)

set(CMAKE_C_FLAGS_INIT "-m32")
set(CMAKE_CXX_FLAGS_INIT "-m32")
set(CMAKE_ASM_FLAGS_INIT "-m32")
set(CMAKE_EXE_LINKER_FLAGS_INIT "-m32")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-m32")
set(CMAKE_MODULE_LINKER_FLAGS_INIT "-m32")

# Debian/Ubuntu multiarch layout for the i386 slice.
set(CMAKE_LIBRARY_ARCHITECTURE i386-linux-gnu)
