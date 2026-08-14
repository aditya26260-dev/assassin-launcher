# Building Assassin Launcher

Every file in this project has been written and manually reviewed, but
**never actually compiled** - there's no Android SDK/NDK/emulator in the
sandbox this was built in. GitHub Actions solves that: GitHub's own
runners have full internet access and a preinstalled Android SDK, so this
is the standard, reliable way to get a first real build without needing
Android Studio on any device.

The workflow is already sitting at `.github/workflows/android-build.yml`.
It builds a debug APK on every push and uploads it (or the error logs, if
it fails) as a downloadable build artifact.

## Repo visibility

Recommend **private** for now, given the standing "no repo until it's
working" call - a private repo gets Actions (free for private repos too)
without putting an untested project in public view. Flip it to public
later in one click (Settings -> General -> Danger Zone -> Change
visibility) whenever it's ready.

## From your phone, using Termux

1. Install **Termux from F-Droid** (`https://f-droid.org/packages/com.termux/`)
   - not the Play Store version - it's outdated and no longer updated.

2. Set up:
   ```
   pkg update && pkg upgrade
   pkg install git unzip gh
   termux-setup-storage
   ```
   (`termux-setup-storage` will prompt for a storage permission - accept
   it, this is what lets Termux see your Downloads folder.)

3. Unzip the project (adjust the filename if it downloaded differently):
   ```
   cd ~/storage/downloads
   unzip assassin-launcher-progress.zip -d ~/assassin-launcher
   cd ~/assassin-launcher/assassin-launcher
   ```

4. Log into GitHub (opens a browser for you to approve - no manual token
   copying needed):
   ```
   gh auth login
   ```
   Choose: GitHub.com -> HTTPS -> Login with a web browser -> follow the
   one-time code it gives you.

5. Create the repo and push in one step:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   gh repo create assassin-launcher --private --source=. --remote=origin --push
   ```

6. Check the build: open the repo on GitHub -> **Actions** tab. A run
   should already be in progress from the push above. Green = there's a
   real debug APK waiting in that run's "Artifacts" section. Red = real
   compile errors - expected on a first build this size, and exactly what
   we want surfaced now rather than later.

## If it's red

Open the failed run -> the "Build debug APK" step -> copy the actual
error text (not just "build failed", the specific compiler/Gradle error
above it) and send it over. Fixing real, compiler-confirmed errors is a
much faster loop than continuing to review code by eye.

## Re-syncing after this doc's own updates

Each time an updated project zip comes through, the simplest path is:
unzip it over the existing `~/assassin-launcher/assassin-launcher`
folder (overwrite), then from inside it:
```
git add .
git commit -m "Update from session"
git push
```
