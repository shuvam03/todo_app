STEP A - upload files
In your GitHub repo: Add file > Upload files. Select ALL files in this folder (Ctrl+A / Cmd+A) and drop them. Commit.
(You can delete the old files first; it doesn't matter if they stay.)

STEP B - create the build file (this is what makes the folder)
Add file > Create new file.
In the name box type exactly:   .github/workflows/build-apk.yml
(typing the "/" creates the folders automatically)
Open build-apk.yml from this folder, copy everything, paste it in the big box. Commit.

STEP C - get the APK
Actions tab > "Build APK" > wait for green tick (3-5 min) > open it > Artifacts > Todo-APK.
Unzip > app-debug.apk > send to phone > open > allow install.
