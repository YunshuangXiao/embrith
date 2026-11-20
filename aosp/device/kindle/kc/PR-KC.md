# Add Colorsoft display support to the shared PW6 runtime

KC uses a 32bpp framebuffer, so the existing display daemon exits before Android can show its UI. Add a runtime-selected RGBA backend while preserving the 8bpp PW6 backend and the existing product/build target.

- Negotiate an optional RGB plane through the existing shared-frame header; retain grayscale for older producers and PW6.
- Show the persistent color/grayscale switch only when the display daemon advertises color support.
- Enable KC hardware page animations in both modes, preserving direction, steps and per-app exclusions.
- Stop and restore the separate native X job for KC sessions.
- Document backend selection and hardware validation in aosp/device/kindle/kc/README.md.

Validation: native host/HWC compilation; runtime capability fixtures and gray/RGBA protocol tests passed on KC; KC physical color, switch, gestures and page animations confirmed. PW6 hardware regression remains outstanding. Full clean AOSP image/release packaging has not been run in this workspace; component-level builds were used for the device trial.

Follow-up device validation: automatic backend selected kc-rgba8888, advertised color_supported=1, allocated the expected 10790656-byte frame and booted Android/Home with persisted COLOR=1.
