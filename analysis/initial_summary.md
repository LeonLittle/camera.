# B153 / T10s firmware initial analysis

## Package integrity

- Release asset: `T10s-VTM1+8G-.-B153_MDHL_001_M135_7.1_20260611.IC.32.zip`
- Asset size: `856,440,776` bytes
- SHA-256: `74a0ec7f85f2a1fe9ccebcdd83bfd31fbb9b30c6afbc87a07d1b1c321e247078`
- The analysis workflow was read-only and did not modify the release asset.

## Confirmed platform and Android version

- SoC/platform: MediaTek `MT6735M`
- Android version: `7.1`
- API level: `25`
- Build ID: `NRD90M`
- Product model/brand: `T10s`
- Product project: `aeon6735m_35_d_n`
- Build display ID: `B153_MDHL_001_M135_7.1_20260611`
- Security patch level: `2017-05-05`
- Build type: `user`
- Build tags: `test-keys`
- CPU userspace is 32-bit only: `armeabi-v7a, armeabi`; no `arm64-v8a` ABI is exposed.

The filename contains `VTM1+8G`, which suggests this firmware package may target a 1 GB RAM / 8 GB storage variant, but that capacity was not independently verified from the current automated report. It should not be treated as confirmed hardware capacity yet.

## Firmware structure

The package contains a classic MediaTek scatter-based flashing set:

- `MT6735M_Android_scatter.txt`
- `preloader_aeon6735m_35_d_n.bin`
- `lk.bin`
- `boot.img`
- `recovery.img`
- `logo.bin`
- `secro.img`
- `trustzone.bin`
- `system.img`
- `cache.img`
- `userdata.img`

No separate `vendor.img` is present. Vendor files are stored inside the `system.img` filesystem.

## system.img format

- Android sparse image, sparse format version 1.0
- Converts successfully to a raw Linux `ext4` filesystem
- Raw filesystem volume name: `system`
- Block size: `4096`
- Sparse output block count: `473,088`
- Approximate raw image size: `1,937,768,448` bytes
- Scatter partition size for `system`: `0x73800000`

This means `system.img` is technically suitable for controlled unpacking, file replacement, filesystem rebuilding and conversion back to Android sparse format.

## Important installed applications

Detected system/product applications include:

- Launchers: `JixiangLauncher.apk`, `Launcher3.apk`
- Camera: `vendor/app/Camera/Camera.apk`
- Gallery/photo: `Gallery2.apk`, `photo.apk`
- Settings/System UI: `Settings.apk`, `SystemUI.apk`
- File manager and music: `FileManager.apk`, `Music.apk`, `Videos.apk`
- Reader: `HonghuReader.apk`
- Product-specific application group: `kdxxj_bdc`, `kdxxj_bdwp`, `kdxxj_download`, `kdxxj_jpkc`, `kdxxj_music`, `kdxxj_scb`, `kdxxj_zncd`, `kdxxj_zndy`
- Product feature: `SuperHearing.apk`
- Input method: `sougou-v1.2.00.apk`
- Engineering/service tools: `EngineerMode.apk`, `FactoryMode.apk`, `DeviceTestApp.apk`, `MTKLogger.apk`, `MTKThermalManager.apk`, `CDS_INFO.apk`, `YGPS.apk`, `BtTool.apk`, `AtciService.apk`

This looks more like a customized Android multimedia/learning-device firmware than a minimal camera-only firmware.

## What can probably be modified first

The lowest-risk first-stage targets are:

1. Replace or configure the default launcher.
2. Preinstall a custom monitoring/streaming APK.
3. Configure boot auto-start for that APK.
4. Remove or disable nonessential product applications after dependency checks.
5. Replace boot animation and selected system resources.
6. Adjust safe system properties such as product-facing name, locale and selected defaults.
7. Keep the existing MediaTek camera, audio, Wi-Fi and hardware abstraction layers unchanged.

For a home-monitoring project, the recommended strategy is to preserve the original kernel, drivers and MediaTek libraries, then modify only the upper Android application layer first.

## Verification and signing status

- The package does not contain a dedicated `vbmeta.img` partition/image.
- The automated string scan found a small number of AVB-like strings, but this is not sufficient to prove Android Verified Boot is active.
- `dm-verity`, boot image verification, APK platform signing and bootloader signature enforcement are not yet conclusively determined.
- `test-keys` in the build fingerprint is encouraging for modification, but it does not prove that generic AOSP test keys will sign every required component successfully.

Before rebuilding a flashable image, the next analysis stage must inspect:

- boot ramdisk `fstab` and `init*.rc`
- `verity` mount flags
- APK signing certificates for launcher, camera and product apps
- package names, activities, services and boot receivers
- current default-home selection and boot auto-start path
- filesystem SELinux labels, permissions, owners and symbolic links

## Do-not-touch partitions for the first version

Do not modify or flash experimental versions of:

- `preloader`
- `lk` / bootloader
- `trustzone` / `tee1` / `tee2`
- `secro`
- `nvram`
- `nvdata`
- `proinfo`
- `protect1` / `protect2`
- `seccfg`
- `oemkeystore` / `keystore`

The first experimental firmware should preferably modify only a copied `system.img`, and possibly `boot.img` later if boot-time changes are genuinely required and recovery is proven.

## Initial conclusion

The firmware is modifiable at the Android application/system-image layer. It is a MediaTek MT6735M Android 7.1, 32-bit, scatter-based firmware with a conventional sparse ext4 `system.img`. A custom home-monitoring interface, auto-starting streaming application, simplified launcher and removal of nonessential apps are realistic. A fully rebuilt low-level system is unnecessary and would add substantial risk.
