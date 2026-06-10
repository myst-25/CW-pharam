# Changelog

All notable changes to the CW Pharmacy PDF Saver will be documented in this file.

## [v1.2.0] - 2026-06-10
### Added
- Injected a global welcome popup dialog when launching CW Pharmacy to confirm module activation.
- Added quick action buttons on the popup to Join the Telegram Community and Star the project on GitHub.

## [v1.0.2] - 2026-06-10
### Changed
- Refined the floating action button UI by replacing "DL" text with a native download icon (⬇️).
- Lowered the minimum SDK requirement to Android 7.0 (API 24) to support older devices.
- Refined GitHub-to-Telegram Action workflows to dynamically inject custom APK names (`cw-pharma-[version]-myst25.apk`).

## [v1.0.1] - 2026-06-10
### Changed
- Stripped away the experimental client-side paywall unlocker.
- Core focus cleanly adjusted strictly to the PDF Downloader & Decryptor features.
- Implemented automated CI/CD Github Actions pipelines for Telegram distribution.

## [v1.0.0] - 2026-06-10
### Added
- Initial Release of the CW Pharmacy module based on `libxposed` API 101.
- Injected custom Material Design Download button securely into the CW Pharmacy PDF Viewer.
- Added intelligent network decryption hooks that automatically fetch XOR/AES protected PDFs, strip their encryption layer, and safely extract the raw, readable `.pdf` to the device's `Downloads` directory.
