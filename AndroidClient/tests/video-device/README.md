# Software HEVC device probe

Run `ANDROID_HOME=... JAVA_HOME=... sh run.sh <adb-serial>` on an authorized Android device. It selects an advertised software HEVC decoder and decodes a locally generated 256×144 Main-profile blue frame into a temporary surface, using the client's initial 1920×1080 MediaFormat. It does not install an app, access the network, connect to a Mac, or read personal content. The frame was generated locally with VideoToolbox. A pass checks this device's decoder only; it does not establish another device's performance or compatibility.
