# Missing Device Guard

Bitwig crashes when a project contains a native device that isn't installed, for example a custom device like VU Meter when its folder isn't registered. Bitwig logs "Device not found", then asks the missing device for its identity and crashes.

## What this mod does

- When a device has no definition and no saved contents, Bitwig gets a stand-in identity instead of crashing.
- The device then shows Bitwig's own "Device missing" panel.
- Each stand-in is written to the log as "missing device", once per place it was asked from.

## Careful

- Don't save over a project while a device in it shows "Device missing": install the device and reopen first. What Bitwig writes for a missing device is untested.
