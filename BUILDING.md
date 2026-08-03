## Building Jamepad

Jamepad's native code lives inline in `ControllerManager.java` and `ControllerIndex.java` as
jnigen `/* ... */` blocks. The build has three stages, and Gradle wires all of them together:

1. **SDL 3** is configured, built and installed per target by CMake into `build/sdl/<target>`
   as a static library (`buildSdl<Target>` tasks).
2. **jnigen** extracts the inline C/C++ into `build/jnigen/jni` (`jnigen` task).
3. **The natives** are compiled and linked against the staged SDL into
   `build/jnigen/libs/<target>` (`jnigenBuild*` tasks).

You never have to run CMake by hand; `jnigenBuildLinux_x86_64` depends on `buildSdlLinux64`
and so on.

### Prerequisites

- **JDK 25.** Gradle will provision one through the Foojay toolchain resolver if it cannot
  find one, but the Gradle daemon itself needs JDK 17 or newer to start.
- **CMake 3.16 or newer** on `PATH` (or pointed at by a `CMAKE` environment variable).
- A C/C++ toolchain for each target you intend to build (see below).

Clone with submodules, or initialise them afterwards:

```sh
git clone --recursive https://github.com/libgdx/Jamepad.git
# or, in an existing clone:
git submodule update --init --recursive
```

### Build targets

| Gradle task | Output | Toolchain |
| --- | --- | --- |
| `jnigenBuildWindows_x86_32` | `windows32/jamepad.dll` | MinGW-w64 i686 (or MSVC, see below) |
| `jnigenBuildWindows_x86_64` | `windows64/jamepad64.dll` | MinGW-w64 x86_64 (or MSVC) |
| `jnigenBuildLinux_x86_32` | `linux32/libjamepad.so` | gcc multilib |
| `jnigenBuildLinux_x86_64` | `linux64/libjamepad64.so` | gcc |
| `jnigenBuildLinux_Arm_32` | `linuxarm32/libjamepadarm.so` | `arm-linux-gnueabihf-gcc` |
| `jnigenBuildLinux_Arm_64` | `linuxarm64/libjamepadarm64.so` | `aarch64-linux-gnu-gcc` |
| `jnigenBuildMacOsX_x86_64` | `macosx64/libjamepad64.dylib` | Xcode clang |
| `jnigenBuildMacOsX_Arm_64` | `macosxarm64/libjamepadarm64.dylib` | Xcode clang |

`jnigenBuildAllWindows`, `jnigenBuildAllLinux` and `jnigenBuildAllMacOsX` build a whole
platform at once, and `jnigenBuildHost` builds only the architecture you are on.

### Building on Windows with MSVC

The Windows natives are normally cross-compiled from Linux with MinGW, which is what CI does.
For a local build or smoke test on Windows, pass `-PwindowsAbi=msvc` to switch the single
Windows x64 target over to `cl.exe`:

```powershell
.\gradlew -PwindowsAbi=msvc jnigenBuildWindows_x86_64
```

This needs Visual Studio Build Tools with the C++ workload; it builds SDL with the static CRT
(`/MT`) to match how jnigen links the native.

### Building on Linux

```sh
sudo apt-get install build-essential cmake pkg-config \
  libasound2-dev libpulse-dev libpipewire-0.3-dev libjack-dev libsndio-dev libudev-dev
# Ubuntu 20.04 has no PipeWire 0.3; get it from ppa:pipewire-debian/pipewire-upstream
./gradlew jnigenBuildLinux_x86_64
```

The other Linux targets need their cross toolchain on top of that, described by the CMake
toolchain files in `sdl_build/`:

```sh
sudo dpkg --add-architecture i386 && sudo apt-get update
sudo apt-get install gcc-multilib g++-multilib linux-libc-dev:i386   # linux32
sudo apt-get install gcc-arm-linux-gnueabihf g++-arm-linux-gnueabihf \
  binutils-arm-linux-gnueabihf libc6-dev-armhf-cross                 # linuxarm32
sudo apt-get install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu \
  binutils-aarch64-linux-gnu libc6-dev-arm64-cross                   # linuxarm64
```

CI installs the versioned `gcc-10-aarch64-linux-gnu` style packages instead, because apt in
the `ubuntu:20.04` image refuses to resolve the unversioned metapackages. If you do the same,
note that jnigen always invokes the compilers as `<triplet>-gcc` and `<triplet>-g++`, so the
unversioned symlinks those metapackages provide have to exist one way or another. SDL itself
is fine either way; the toolchain files accept both spellings.

CI deliberately builds on `ubuntu:20.04` rather than anything newer, because the natives
inherit the glibc of whatever compiles them and 20.04 is the oldest distro Jamepad targets.
Building on 22.04 silently raises the requirement to glibc 2.34 and breaks 20.04 and Debian
11 users. Three consequences follow. Focal has no `gcc-11-*-cross` packages, hence 10 above.
Its CMake is 3.16.3, exactly SDL's minimum, so CI installs an upstream CMake over it. And it
only carries PipeWire 0.2, so CI takes the 0.3 headers from
`ppa:pipewire-debian/pipewire-upstream`, which is where the pre-SDL3 builds got them too.

The `-dev` packages above are only needed once, for the host architecture. SDL dlopens ALSA,
PulseAudio, PipeWire, sndio and udev at runtime and only ever compiles against their headers,
which are architecture independent, so the 32-bit and ARM builds reuse the native ones. A
header that is missing costs you that backend rather than failing the build. D-Bus is the one
exception, since `dbus-arch-deps.h` *is* architecture specific — it is switched off explicitly
because nothing outside SDL's video code needs it.

Windows cross-compilation additionally needs `g++-mingw-w64-i686` and `g++-mingw-w64-x86-64`.
Those targets use SDL's own toolchain files under `SDL/build-scripts/`.

### Building on macOS

```sh
./gradlew jnigenBuildAllMacOsX
```

Both macOS slices are built from an arm64 or x86_64 Mac; SDL is configured with
`CMAKE_OSX_ARCHITECTURES` per target. The deployment target is **10.13**, which is the oldest
macOS SDL 3 supports.

### Packaging

```sh
./gradlew jar                    # jamepad.jar with whatever natives are in build/jnigen/libs
./gradlew jnigenPackageAllDesktop # jamepad-platform natives-desktop artifact
```

Because each target's natives land in `build/jnigen/libs`, the usual CI flow is to build the
macOS natives on a Mac runner, upload them as an artifact, download them into
`build/jnigen/libs` on the Linux runner, and let the Linux job package everything. See
[.github/workflows/pushaction.yml](.github/workflows/pushaction.yml).

### Notes on the SDL build

- SDL is built **static only**, with video, render, GPU, camera, dialog and tray disabled.
  Audio stays on because the DualSense haptics path opens the controller as an audio device.
- `SDL_DYNAMIC_API` is patched off in `SDL/src/dynapi/SDL_dynapi.h` by the `patchSdlDynamicApi`
  task. SDL does not allow that to be set from the command line, and leaving it on would let
  an environment variable redirect our statically linked SDL to some other build.
- The natives are compiled with `-DSDL_DECLSPEC=` so the statically linked SDL symbols are not
  re-exported out of `libjamepad`, where they would collide with any other SDL in the process.
- The link line comes from the `sdl3.pc` that CMake installs into the staging prefix, with
  `-lSDL3` rewritten to the absolute path of the archive.

### Updating the SDL submodule

```sh
cd SDL
git checkout release-x.y.z
cd ..
git add SDL
git commit -m "Update to SDL x.y.z"
```
