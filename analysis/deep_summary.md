# B153 / T10s deep analysis conclusions

## Boot and verification

The Android boot image unpacked successfully. It contains a 32-bit ARM Linux kernel and a gzip ramdisk. Important boot properties are:

- `ro.adb.secure=1`
- `ro.secure=1`
- `ro.debuggable=0`
- `persist.sys.usb.config=mtp`

The boot ramdisk contains a `verity_key`. In `fstab.mt6735`, the system partition is mounted with:

```text
/system ext4 ro wait,verify
```

The userdata partition is mounted with `forceencrypt` using the metadata partition.

This confirms that direct modification of `system.img` is not enough. A changed system image will probably fail verified mounting unless the verity data is regenerated with the expected key or the `verify` flag is deliberately removed from a copied and repacked `boot.img`. Bootloader acceptance of a modified `boot.img` must still be tested safely before flashing.

## Selected application findings

### MediaTek camera

- Package: `com.mediatek.camera`
- Version: `1.1.40030`
- Main activity: `com.android.camera.CameraActivity`
- Uses the normal camera, audio and storage permissions.
- Certificate SHA-256: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`

The same certificate is used by several core packages, including Gallery, Settings and SystemUI. This strongly suggests a shared Android platform/system signing set for those components.

### Standard Launcher3

- Package: `com.android.launcher3`
- Version: `7.1`
- Declares the Android `HOME` category.
- Receives `BOOT_COMPLETED`.
- Certificate SHA-256: `28bbfe4a7b97e74681dc55c2fbb6ccb8d6c74963733f6af6ae74d8c3a6e879fd`

Launcher3 is the conventional Android home application and is a clean fallback target for early testing.

### Customized Jixiang launcher

- Package: `com.jxw.launcher`
- Version: `20251124.11`
- Display label: `学习辅导`
- Requests Wi-Fi, network, camera, storage, package-install and privileged phone-state permissions.
- Receives `BOOT_COMPLETED`, connectivity changes and battery events.
- Starts several custom platform, login, media and download services.

Standard `apksigner` verification reports an inconsistent or stripped v2 signature on this APK. Several `kdxxj_*` product applications show the same condition. These APKs should not be casually unpacked, edited and rebuilt in place. For the monitoring conversion, replacing or disabling the customized product layer is safer than patching these APKs internally.

## Practical conversion route for home monitoring

The most realistic first test firmware is:

1. Preserve the existing MediaTek kernel, drivers, camera HAL, audio HAL, Wi-Fi libraries and vendor binaries.
2. Add a separately signed monitoring/streaming APK to the copied system image.
3. Use Launcher3 temporarily, or install a new dedicated monitoring launcher that declares the `HOME` category.
4. Configure boot auto-start in the new app through `BOOT_COMPLETED` and a foreground service.
5. Remove or disable Jixiang and `kdxxj_*` applications only after confirming no hardware services depend on them.
6. Repack a copied `system.img` while preserving ext4 ownership, permissions, symbolic links and SELinux labels.
7. Handle dm-verity by regenerating verification metadata or using a test-only copied `boot.img` with the system `verify` flag removed.
8. Flash only `boot` and `system` during controlled testing; never include `preloader`, `lk`, `trustzone`, `secro`, `nvram`, `nvdata`, `proinfo` or protect partitions.

## Current feasibility verdict

A custom monitoring system is technically feasible, but the first flashable image requires both a modified `system.img` and a verified plan for the boot ramdisk's dm-verity configuration. The safest software prototype remains an installable APK tested on the untouched original firmware before any image is rebuilt.
