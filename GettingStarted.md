# Installation #


  1. Everything described here is done under Linux-base system.
  1. First you need to download TinyOS toolchain from here http://www.tinyos.net/
  1. As soon as you have TinyOS installed in you system, download ConesC.zip from here https://www.dropbox.com/s/16m45nf5w1f44gn/ConesC.zip
  1. To install ConesC toolchain, just unzip ConesC.zip and launch `./install` with root privileges.
  1. To remove all the ConesC components from your system, just launch `./uninstall` with root privileges.


# Usage #

To build a binary from ConesC sources just launch cncmake, as for example `cncmake telosb` to build a bin for TelosB platform. You can also enable verbose mode by using a modifier `-v` as in: `cncmake telosb -v`, so generated files will not be deleted after compilation. It may be useful for checking gcc compilation problems: generate code first `cncmake telosb -v` and then launch gcc `make telosb`.

# Demo part #

The demonstration is divided into three parts:

  1. Firmware for a sensor mode. `Sensor`
  1. Firmware for a base-station. `Base Station`
  1. Software for PC. `Serial Station`

The data acquired from the sensor is transmitted to the base-station and then to the PC, where it is displayed.

  1. Once you have ConesC toolchain installed in your system, download and unzip Demo.zip from here https://www.dropbox.com/s/3ai4bmg6oaennpc/Demo.zip
  1. Compile the sources and install binaries to the motes:
```
cncmake telosb
make telosb reinstall, /dev/ttyUSB0
```
  1. Launch serial station from the `Serial Station` folder. If your base-station is running on USB0, serial station should be launched like this:
```
sh run USB0
```

To see, on which port your mote is running, launch:
```
motelist
```
You will probably see something like this:
```
Reference  Device           Description
---------- ---------------- ---------------------------------------------
XBS5H6PH   /dev/ttyUSB0     XBOW Crossbow Telos Rev.B
```
Which means your node is running on USB0.

For mode details see http://tinyos.stanford.edu/tinyos-wiki/index.php/Quickstart:_TelosB