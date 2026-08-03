# Jamepad Fork

#### A better way to use gamepads in Java.

*This is a fork of and based on the [original work by William Hartman](https://github.com/williamahartman/Jamepad/tree/ae170a95eb7c14d82b19328480b1ab5a45b77001)*.

Jamepad is a library for using gamepads in Java. It's based on SDL 3 ([here](https://www.libsdl.org/)) and uses jnigen ([more info here](https://github.com/libgdx/libgdx/wiki/jnigen)). We also use [this](https://github.com/gabomdq/SDL_GameControllerDB) really nice database of gamepad mappings.

Jamepad has:
  - One library that supports all platforms (Windows, OSX, and Linux)
  - XInput support on Windows for full Xbox 360 controller support.
  - Support for plugging/unplugging controllers at runtime.
  - Support for rumble
  - Button/Axis mappings for popular controllers.
  - A permissive license (see below).

This fork improved the following points compared to last real [Jamepad version 1.3.2](https://github.com/williamahartman/Jamepad/tree/ae170a95eb7c14d82b19328480b1ab5a45b77001):
* This fork builts the native library on GitHub Actions. You can see all the magic happen there. Moreover, if you fork this repo and adjust settings, you are immediately ready to go with your own build! We are open for PRs though.
* New features added, newer SDL version used
* Natives are smaller in size
* Natives for arm architecture included

#### Upgrading from the SDL 2 versions of this fork

The move to the SDL 3 gamepad API forced a few breaking changes:

* `ControllerPowerLevel` no longer has charge buckets (`POWER_EMPTY`/`POWER_LOW`/...). SDL 3
  reports a power *state* and a percentage separately, so the enum now mirrors
  `SDL_PowerState` (`POWER_ON_BATTERY`, `POWER_CHARGING`, `POWER_CHARGED`, ...) and
  `ControllerIndex.getBatteryPercentage()` returns the 0-100 charge (-1 if unknown).
* `ControllerButton` gained `BUTTON_MISC2` through `BUTTON_MISC6`. `A`/`B`/`X`/`Y` still mean
  the same physical buttons; SDL 3 calls them `SOUTH`/`EAST`/`WEST`/`NORTH`.
* Motion sensors are no longer switched on by `Configuration.useSonyControllerFeatures`. Set
  `Configuration.useControllerMotionSensors` instead, which works for every controller with
  motion hardware rather than only Sony pads.
* `SensorState.getTimestamp()` now returns the hardware sample time in **nanoseconds**. It
  used to return microseconds since the Unix epoch. The new value comes from the driver, so
  it tracks the real sampling interval, but its origin is arbitrary and only meaningful when
  compared against other timestamps from the same source.
* Jamepad now requires JDK 25 to build. The natives themselves are built for Windows 10+,
  macOS 12+ and glibc 2.31+ (Ubuntu 20.04, Debian 11), on x86, x86_64, ARM32 and ARM64.

#### Motion sensors

Gyroscope and accelerometer support is off by default, because motion reporting noticeably
increases how much a controller sends over USB or Bluetooth. Turn it on per manager:

````java
Configuration configuration = new Configuration();
configuration.useControllerMotionSensors = true;

ControllerManager manager = new ControllerManager(configuration);
manager.initSDLGamepad();

ControllerIndex controller = manager.getControllerIndex(0);
if (controller.isSupportingSensorData()) {
    SensorState state = controller.getSensorState();
    // accelerometer in m/s^2 including gravity, gyroscope in rad/s
}
````

This covers every controller SDL reports motion for, including DualShock 4 and DualSense
pads, Switch Pro controllers and Joy-Cons, and the Steam Deck's built-in controller. A pad
may have only one of the two sensors, so check `isSupportingAccelerometer()` and
`isSupportingGyroscope()` if you care which axes are live.

Handhelds whose IMU is not part of a gamepad, such as the ROG Ally or the Legion Go, expose
it as a system sensor instead. Enable `Configuration.useSystemMotionSensors` and read it
through `ControllerManager.getSystemMotionSensors()`. SDL only implements system sensors on
Windows, Android and a few consoles, so check `isAvailable()` before relying on it. Because
handhelds differ in how the IMU is physically mounted, you will usually still need your own
per-device axis correction there.

SDL does not provide fused orientation. If you need a quaternion, run the raw gyroscope and
accelerometer readings through your own filter.

#### Stuff You Should Know About Jamepad

- On Windows (only 7 and up were tested), no special dependencies are needed.
- On Linux, runtime dependencies are: libevdev, libudev (normally included)
- On OS X, no special dependencies are needed

#### Current Limitations
- The order of gamepads on Windows is not necessarily the order they were plugged in. XInput controllers will always appear before DirectInput controllers, regardless of when they were plugged in. This means that the player numbers associated with each controller can change unexpectedly if XInput controllers are plugged in or disconnected while DirectInput controllers are present.
- If using getState() in ControllerManager, a new ControllerState is instantiated on each call. For some games, this could pose a problem.



## Using Jamepad

For usage within libgdx project, take a look at [gdx-controllers](https://github.com/libgdx/gdx-controllers).
The following information is only needed for non-gdx Java projects.

### Getting Jamepad

[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.badlogicgames.jamepad/jamepad?nexusVersion=2&server=https%3A%2F%2Foss.sonatype.org&label=release)](https://search.maven.org/artifact/com.badlogicgames.jamepad/jamepad)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.badlogicgames.jamepad/jamepad?server=https%3A%2F%2Foss.sonatype.org&label=snapshot)](https://oss.sonatype.org/#nexus-search;gav~com.badlogicgames.jamepad~jamepad)

##### gradle
If you use gradle, you can pull this package in from Maven Central.
Add this line to your dependencies section. Update the version number to whatever the latest release is.
````
dependencies {
  ...
  implementation 'com.badlogicgames.jamepad:jamepad:3.0.0.0'
}
````
##### maven
If you use gradle, you can pull this package in from Maven Central.
Add this line to your dependencies section. Update the version number to whatever the latest release is.
````
<dependencies>
    ...
    <dependency>
        <groupId>com.badlogicgames.jamepad</groupId>
        <artifactId>jamepad</artifactId>
        <version>3.0.0.0</version>
    </dependency>
</dependencies>
````

#### Using Jamepad
There are two main ways to use Jamepad. Both rely on a ControllerManager Object.

```java
ControllerManager controllers = new ControllerManager();
controllers.initSDLGamepad();
```

For most applications, using the getState() method in ControllerManager is best. This method returns an immutable ControllerState object that describes the state of the controller at the instant the method is called. Using this method, you don't need to litter code with a bunch of exception handling or handle the possibility of controller disconnections at weird times. 

If a controller is disconnected, the returned ControllerState object has the isConnected field set to false. All other fields are either false (for buttons) or 0 (for axes).

Here's a simple example:

```java
//Print a message when the "A" button is pressed. Exit if the "B" button is pressed 
//or the controller disconnects.
while(true) {
  ControllerState currState = controllers.getState(0);
  
  if(!currState.isConnected || currState.b) {
    break;
  }
  if(currState.a) {
    System.out.println("\"A\" on \"" + currState.controllerType + "\" is pressed");
  }
}
```

For a select few applications, getState() might not be the best decision. Since ControllerState is immutable, a new one is instantiated on each call to getState(). This should be fine for normal desktop JVMs; both Oracle's JVM and the OpenJDK one should absolutely be able to handle this. What problems do come up could probably be solved with some GC tuning.

If these allocations do end up being an actual problem, you can access the internal representation of the controllers. This is more complicated to use, and you might need to deal with some exceptions.

Here's a pretty barebones example:

```java
//Print a message when the "A" button is pressed. Exit if the "B" button is pressed 
//or the controller disconnects.
ControllerIndex currController = controllers.getControllerIndex(0);

while(true) {
  controllers.update(); //If using ControllerIndex, you should call update() to check if a new controller
                        //was plugged in or unplugged at this index.
  try {
    if(currController.isButtonPressed(ControllerButton.A)) {
      System.out.println("\"A\" on \"" + currController.getName() + "\" is pressed");
    }
    if(currController.isButtonPressed(ControllerButton.B)) {
      break;
    }
  } catch (ControllerUnpluggedException e) {   
    break;
  }
}
```

When you're finished with your gamepad stuff, you should call quitSDLGamepad() to free the native library.

```java
controllers.quitSDLGamepad();
```

#### Using DualSense/ DualShock features

You can also access Sony specific controller features like touchpad, sensor data (gyroscope/ accelerometer) and adaptive
triggers with this library. In order to activate these features you have to initialize the ControllerManager like this

```java
final Configuration configuration = new Configuration();
configuration.useSonyControllerFeatures = true;
ControllerManager controllers = new ControllerManager(configuration);
```

If this Sony controller feature is enabled and your controller supports touchpad data and sensor data, ControllerState
will be  filled with 3 additional objects every time you call getState(). Two TouchState objects and one SensorData
object. ControllerIndex will reuse already created objects instead of creating new ones.

Sending adaptive trigger data only has an effect if the connected controller is a DualSense controller. If you want to
send adaptive trigger data to the DualSense with index 0 you can do it like this

```java
/* Constant resistance across entire trigger pull */
byte leftTriggerEffect = 0x01;
byte[] leftAdaptiveTriggerData = new byte[]{ 0, 110, 0, 0, 0, 0, 0, 0, 0, 0 };
/* Resistance and vibration when trigger is pulled */
byte rightTriggerEffect = 0x06;
byte[] rightAdaptiveTriggerData = new byte[]{ 15, 63, (byte) 128, 0, 0, 0, 0, 0, 0, 0 };

controllers.sendAdaptiveTriggers(0, leftTriggerEffect, leftAdaptiveTriggerData, rightTriggerEffect, rightAdaptiveTriggerData);
```

More information about the adaptive trigger data can [be found here](https://controllers.fandom.com/wiki/Sony_DualSense#FFB_Trigger_Modes).

## Building Jamepad

See [BUILDING](BUILDING.md)

## License

The original work by William Hartman is licensed under the permissive zLib license.
You can include this use this library in proprietary projects without sharing source, and you are allowed to alter the project.
The original license is kept [here](LICENSE_hartman.txt).

libSDL 3 is [zLib licensed](https://libsdl.org/license.php), too.

Every work done in this fork is licensed under Apache 2 License conditions, see LICENSE file.