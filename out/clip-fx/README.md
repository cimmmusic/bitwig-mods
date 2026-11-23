# Clip FX

Effects for a single clip, like Studio One's Event FX. Select a clip in the arranger, turn on **Clip FX** in the Inspector, and add effects that only that clip plays through. The rest of the track stays dry, and effect tails (reverb, delay) ring out after the clip ends.

**Beta.**

## Using it

1. Select an arranger clip.
2. In the Inspector's **Clip FX** block, set **Clip FX** to **On**.
3. Click **+** to add effects with Bitwig's own browser.

The effects list works like the mixer's device list:
- click an effect to select it;
- double-click it to open it;
- drag it up or down to change the order;
- right-click it, or press Backspace, to remove it.

**Show FX** opens the clip's effects in the device panel. Set **Clip FX** to **Off** to bypass them; your effects are kept.

### What happens to your clips

- **Moving a clip:** drag it along the timeline and its effects move with it.
- **Renaming a clip:** its effects follow the new name.
- **Deleting a clip:** its effects are removed too, so nothing keeps running in the background.
- **Saving:** Clip FX is saved with the project.

## How it works

Each clip with Clip FX gets its own effects device on its track, called **Clip FX – ⟨clip name⟩**. It's an FX Layer with a dry layer and an FX layer. The clip's own automation switches between them, so the effects play only while the clip plays.

These devices are hidden from the track's device chain and from the mixer: you see the effects in the Inspector, where the clip is. The settings are kept in a hidden block in the master track's comment.

## Known limits

- **Arranger clips only.** Launcher clips and single audio events aren't supported yet.
- **Copy and paste:** a pasted clip plays through the **same** effects as the original.
- **Moving a clip to another track:** its effects are removed.
- **Undo after deleting a clip:** may not bring the clip's effects back. This is being worked on.

*Requires the Patchbay Mod Loader (BML), the Inspector framework and the Action Menu framework. An unofficial mod, not made or supported by Bitwig.*
