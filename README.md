# ![alt text](https://github.com/KumarManas04/NotesSync/blob/master/Images/icon.png)NotesSync
Notes Sync is the answer to your everyday note taking requirements. It was designed with the primary goal of being privacy oriented and convenient. Taking notes, creating to-do lists, setting reminders, adding images and sharing with others, everything is just one tap away. And, it's all instantly searchable.

With Notes Sync you can sync all of your notes, to-do lists and reminders to your <b>own Google Drive or Dropbox accounts.</b> So, everything is synced across your devices and backed up to the cloud. But the best part is, everything goes directly from the app to your account. Also your data can be encrypted with <b>AES 256-bit encryption.</b> And nothing goes through our servers. This ensures <b>Total Privacy.</b>

Other features include themes, colored notes and app lock.

Download it from Google Play Store - https://play.google.com/store/apps/details?id=com.infinitysolutions.notessync

## Screenshots
![alt text](https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_1.png)
<img src="https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_2.png" height="460" width="280">
<img src="https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_3.png" height="460" width="280">
<img src="https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_4.png" height="460" width="280">
<img src="https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_5.png" height="384" width="234">
<img src="https://github.com/KumarManas04/NotesSync/blob/master/Images/screenshot_6.png" height="307" width="614">
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
   - In your strings.xml and AndroidManifest.xml replace `YOUR_APP_KEY_HERE` with the key copied in previous step
6. Build and run the app

## Developed by
* Kumar Manas - <kumarmanas04@gmail.com>

## Licence
```
MIT License

Copyright (c) 2019 Kumar Manas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
