# Fake GPS — com.shavlegi.fakegps

მარტივი fake location აპი Android-ზე. რუკაზე ირჩევ ადგილს, აჭერ „დაწყებას" და ტელეფონი იმ ადგილას „გადადის" — Google Maps-იც და WhatsApp-ის ლოკაციის გაზიარებაც ყალბ ლოკაციას აჩვენებს.

Simple fake location app for Android. Pick a place on the map, press Start, and the phone "teleports" there — Google Maps and WhatsApp live location sharing show the fake location.

## რა შეუძლია / What it does

- რუკა — OpenStreetMap (უფასო, API key არ უნდა) / OpenStreetMap map (free, no API key)
- რუკაზე დაჭერით ადგილის არჩევა / Tap the map to pick a place
- მისამართის/ადგილის ძებნა (Nominatim) / Address/place search (Nominatim)
- დაწყება / გაჩერება (Start / Stop) — foreground service, მუშაობს ფონშიც
- ფავორიტები — შენახული ადგილები, ერთი დაჭერით ტელეპორტი / Favorites, one-tap teleport
- მიმდინარე ყალბი კოორდინატების ჩვენება / Shows the current fake coordinates
- ენა: ქართული (ნაგულისხმები) / English / Русский / Language: Georgian (default) / English / Russian
- მუქი თემა (შავი / მუქი ლურჯი / თეთრი) / Dark theme (black / dark blue / white)

## რაც არ შეუძლია / What it cannot do

- ჯოისტიკი და მარშრუტები — v1-ში არ არის / No joystick or routes in v1
- ანტი-დეტექცია — შეუძლებელია ჩვეულებრივი აპით. აპი იყენებს Android-ის ოფიციალურ mock location სისტემას, ამიტომ აპები, რომლებიც mock-ლოკაციას ამჩნევენ (თამაშები, ბანკები), ამასაც ამჩნევენ. / No anti-detection — impossible for a normal app. It uses Android's official mock location system, so apps that detect mock locations will detect this too.
- ტელეფონის გადატვირთვის მერე თავიდან უნდა ჩართო. / After a phone restart you need to turn it on again.

## დაყენება / Installation

1. ჩამოტვირთე APK GitHub Actions-დან (Actions → Build debug APK → artifact `fake-gps-debug-apk`) ან ააწყე თავად: `./gradlew assembleDebug`
2. დააინსტალირე APK ტელეფონზე
3. Settings → About phone → Build number-ზე დააჭირე 7-ჯერ (Developer options ჩაირთვება)
4. Developer options → Select mock location app → აირჩიე Fake GPS
5. გახსენი აპი, აირჩიე ადგილი რუკაზე და დააჭირე „დაწყებას"

## აწყობა / Build

- Android Studio ან GitHub Actions: `./gradlew assembleDebug`
- minSdk 26, targetSdk 34, Kotlin 2.0.21, AGP 8.5.2, JDK 17
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## ტექნიკური შენიშვნა / Technical notes

- Mock location: `LocationManager` test provider (`addTestProvider` + `setTestProviderLocation`, 1 წამში ერთხელ). Root არ უნდა.
- რუკა: osmdroid 6.1.20 (OpenStreetMap). ძებნა: Nominatim (`nominatim.openstreetmap.org`).
- ფავორიტები და ენა ინახება მხოლოდ ტელეფონზე (DataStore). არაფერი იგზავნება არსად.
- Mock location only changes GPS coordinates — it does not change the IP address, Wi-Fi or cell-tower location.
