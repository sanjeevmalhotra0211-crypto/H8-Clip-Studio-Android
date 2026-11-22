# H8 Clip Studio — Android Device Processing Companion

## Is package mein kya hai
- Android Studio source project (Kotlin).
- Android native Media3 Transformer engine.
- Android document picker se local video selection.
- 60 / 90 / 120 second clips.
- Sequential processing: ek waqt mein ek clip export hoti hai.
- Output: `Movies/H8 Clip Studio`.
- Movie ko website/VPS par upload nahi karta.

## 5 GB file support ka matlab
App 5 GiB tak ki source file select karne deta hai. Android Media3 file ko URI se read karta hai; poori movie ko RAM mein load nahi karta. Lekin 5 GB ka export har phone par guarantee nahi hai. Codec/decoder/encoder support, RAM, battery temperature, available storage aur Android version matter karte hain. Output size source se bada bhi ho sakta hai, isliye source ke alawa kaafi free space rakhein (practical target: source size ke 2x ya zyada).

## Build APK
1. Android Studio install karein (recent stable version).
2. `H8_Clip_Studio_Android` folder open karein.
3. Gradle sync hone dein (internet required; Media3 dependencies Google Maven se aayengi).
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK `app/build/outputs/apk/debug/app-debug.apk` mein milega.

## Important current limitations
- Ye local-processing companion ka first source build hai, production-tested APK nahi.
- App ko processing ke dauran foreground mein khula rakhein; Android battery optimization ya app kill hone se job ruk sakta hai.
- Output MP4 hai; input codec/device encoder combination unsupported ho to export fail ho sakta hai.
- Current version sirf clip splitting karta hai. Full editor effects, captions, filters, login/account sync aur website Projects sync abhi is Android companion mein wired nahi hain.
- App account login ya internet connection nahi maangta; source video local hi rehti hai.
- Android Studio build/signing ke baad hi installable APK generate hoga. ZIP source package hai, prebuilt APK nahi.

## Website se relation
Existing PHP website ko replace nahi karta. Ye companion Android app hai jo large local videos process karne ke liye banaya gaya hai. Website par clips upload/sync karne ka option intentionally automatic nahi hai.


## Laptop nahi hai? Phone se cloud build (GitHub Actions)
1. ZIP extract karein phone ke File Manager se.
2. Browser mein github.com par account banayein/login karein.
3. New repository banayein, naam `H8-Clip-Studio-Android` rakhein, Public ya Private choose karein.
4. Extracted project ke saare files/folders repository ke root mein upload karein. `.github/workflows/build-apk.yml` bhi upload hona zaroori hai. GitHub website mobile par folder upload support na kare to Chrome menu se Desktop site try karein.
5. Files upload/commit hone ke baad repository ke Actions tab mein `Build H8 Clip Studio APK` workflow select karein aur `Run workflow` dabayein.
6. Build successful hone par workflow run ke bottom mein Artifacts se `H8-Clip-Studio-debug-apk` download karein. ZIP extract karke `app-debug.apk` install karein. Android Settings mein browser/file manager ke liye Install unknown apps permission allow karni pad sakti hai.

Cloud build workflow APK banane ki koshish karta hai; source compile errors aayein to Actions ke build log se unhe fix karna hoga. Ye debug APK hoga, Play Store release-signed APK nahi.
