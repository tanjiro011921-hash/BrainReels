# GitHub e upload kore APK build korar steps

1. github.com e notun **empty repository** banan (name: BrainReels, README/license add korben na).
2. Zip unzip kore folder e terminal kholen (Git installed thakte hobe) ebong cholan:

```
cd BrainReels
git init
git add -A
git commit -m "Initial BrainReels"
git branch -M main
git remote add origin https://github.com/<apnar-username>/BrainReels.git
git push -u origin main
```
(Browser e drag-and-drop korle `.github` hidden folder miss hote pare, tai git command use korun.)

3. Repo → **Actions** tab → "Build debug APK" run dekhun (na chollei "Run workflow").
4. Sobuj tick hole run e click → niche **Artifacts** → `brainreels-debug-apk` download → unzip → `app-debug.apk` phone e install.
5. Lal (fail) hole: run → fail howa step → log er shesh 40-50 line copy kore amake pathan.
