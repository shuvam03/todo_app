HOW TO GET YOUR APK (free, no software needed)

1. Create a free account at https://github.com
2. Click "+" (top right) > "New repository". Name it "todo-app", choose Public or Private, click "Create repository".
3. On the new repo page click "uploading an existing file".
   Drag EVERYTHING inside this "todo-android" folder (including the hidden ".github" folder) into the page, then click "Commit changes".
   Tip: on Mac press Cmd+Shift+. in Finder to see the hidden ".github" folder. On Windows: View > Show > Hidden items.
4. Click the "Actions" tab. A job called "Build APK" runs (~3-5 minutes). Wait for the green tick.
5. Click the finished run, scroll to "Artifacts", download "Todo-APK" (a zip). Unzip it to get app-debug.apk.
6. Send app-debug.apk to your phone (Google Drive, email, USB). Open it on the phone.
   Android will ask to allow "Install unknown apps" for that app (Files/Drive/Chrome) - allow it, then tap Install.

Done: "Todo" appears in your app drawer, works offline, and saves your tasks on the phone.

ALTERNATIVE (if you have Android Studio): File > Open > select this folder > Build > Build APK(s).
