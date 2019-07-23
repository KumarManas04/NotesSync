# NotesSync
Notes Sync is the answer to your everyday note taking requirements. It was designed with the primary goal of being convenient to the user. Take notes, create to-do lists, set reminders, and share with others everything is just one tap away. And it's all instantly searchable.

With Notes Sync users can sync all of their notes, to-do lists and reminders to their <b>own Google Drive or Dropbox accounts respectively.</b>
No server is required for syncing the data from the app. Everything goes directly from the app to user's account's storage all in just one tap.

## How to build the project
1. Clone the project - ``` git clone https://github.com/KumarManas04/NotesSync.git```
2. Import the project into Android Studio
3. Enable the Google Drive API for your project - 
   - [Follow this link](https://ammar.lanui.online/integrate-google-drive-rest-api-on-android-app-bc4ddbd90820) Only till Step 3  and make sure you select the scope `..auth/drive.file`
   - Make sure you add the SHA-1 key from your project into the credentials screen
5. Enable Dropbox API for your project -
   - [Open Dropbox App Console](https://www.dropbox.com/developers/apps?_tk=pilot_lp&_ad=topbar4&_camp=myapps)
   - Select Dropbox API
   - Select App folder as access type
   - Set App Name to Notes Sync
   - Click on Create App
   - From the next screen copy your App Key
   - In your strings.xml and AndroidManifest.xml replace `YOUR_APP_KEY` with the key copied in previous step
6. Build and run the app
