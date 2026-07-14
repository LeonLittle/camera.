# Firmware v1 vs v2 conclusion

## Result

Firmware v2 is clearly easier to modify at the Android system layer, but it should not be flashed wholesale onto a device currently running firmware v1 without first confirming the exact hardware variant.

## Why v2 is easier

Firmware v1:

- Project: `aeon6735m_35_d_n`
- Build type: `user`
- 32-bit Android userspace only: `armeabi-v7a, armeabi`
- `ro.adb.secure=1`
- `ro.debuggable=0`
- USB mode: `mtp`
- `/system` is mounted with `wait,verify`, confirming dm-verity-style system verification.
- userdata uses `forceencrypt`.

Firmware v2:

- Project: `aeon6735m_65_d_n`
- Build type: `userdebug`
- 64-bit and 32-bit ABI support: `arm64-v8a, armeabi-v7a, armeabi`
- `ro.adb.secure=0`
- `ro.debuggable=1`
- USB mode: `mtp,adb`
- The `/system ... wait,verify` line is absent from the extracted `fstab.mt6735`.
- userdata still uses `forceencrypt`.

Therefore v2 is substantially friendlier for development: ADB is enabled by default, ADB authentication is disabled, debugging is enabled, and the system partition is not configured with the same `verify` mount flag found in v1.

## Compatibility warning

The two firmware packages are not identical hardware builds:

- v1 preloader: `preloader_aeon6735m_35_d_n.bin`
- v2 preloader: `preloader_aeon6735m_65_d_n.bin`
- v1 system partition size: `0x73800000`
- v2 system partition size: `0x83800000`
- v1 Android userspace is 32-bit only.
- v2 Android userspace exposes 64-bit and 32-bit ABIs.
- v2 filename includes `800w.1300w`, suggesting a camera-module-specific variant.

The different project identifiers, preloaders, architecture configuration and system partition sizes mean that v2 must not be treated as a drop-in replacement for v1. Do not flash v2's preloader, lk, trustzone, secro, nvram, nvdata or other low-level partitions onto the v1 device.

## Recommended route

1. Treat v2 as the preferred development base only if the actual device is confirmed to match the `aeon6735m_65_d_n` / 800w+1300w hardware variant.
2. If the physical device currently runs v1 and its camera/display/touch hardware differs, keep v1's hardware-specific boot/kernel/vendor components.
3. First test whether v2 can boot using only safe, recoverable partitions and a proven SP Flash Tool recovery path; never include the preloader in the first test.
4. The safest immediate approach is still to install and test the monitoring APK on the original system through ADB. v2 makes this much easier because `adb` and debugging are enabled by default.
5. For a final custom firmware, use v2's development-friendly boot configuration only after confirming kernel and hardware compatibility; otherwise modify v1 and deliberately handle or remove its system verification.

## Bottom line

- Easiest to modify: **firmware v2**.
- Safest guaranteed match for the currently running v1 device: **firmware v1**, until hardware compatibility is confirmed.
- Best practical next step: connect the device by USB and test ADB/hardware identity before flashing either modified firmware.
