/*
 * Where Is My Bus (WIMB) - tiny English / Bengali (বাংলা) language layer.
 *
 *  - tr("English text", {n: 3})  -> translated text; "{n}" placeholders are filled in.
 *    English is the key, so a missing translation simply shows English.
 *  - Static HTML is translated automatically by matching its text (no markup changes needed).
 *  - Any element with data-lang-toggle becomes the EN / বাং switch.
 *  - The choice is saved in localStorage ("wimb_lang"); switching reloads the page so that
 *    everything (including live-rendered content) is drawn in the chosen language.
 *
 * Load this file BEFORE the other scripts on every page.
 */
(function () {
  "use strict";
  var STORE = "wimb_lang";
  var BN = {
  "Where Is My Bus": "আমার বাস কোথায়",
  "Real-time bus tracking": "রিয়েল-টাইম বাস ট্র্যাকিং",
  "© 2025 Where Is My Bus · Built by Deep Saha": "© 2025 আমার বাস কোথায় · নির্মাতা: Deep Saha",
  "Where Is My Bus logo": "আমার বাস কোথায় লোগো",
  "Search": "খুঁজুন",
  "Live": "লাইভ",
  "LIVE": "লাইভ",
  "Now": "এখন",
  "Show": "দেখান",
  "Stop": "বন্ধ করুন",
  "Loading stops...": "স্টপ লোড হচ্ছে...",
  "Loading…": "লোড হচ্ছে…",
  "Bus Number": "বাস নম্বর",
  "Route": "রুট",
  "Destination": "গন্তব্য",
  "Today": "আজ",
  "Next": "পরবর্তী",
  "Here": "এখানে",
  "Passed": "পেরিয়েছে",
  "Arrived": "পৌঁছেছে",
  "Arriving": "পৌঁছাচ্ছে",
  "Departed": "ছেড়ে গেছে",
  "Off route": "রুটের বাইরে",
  "Running": "চলছে",
  "Updating": "হালনাগাদ হচ্ছে",
  "Find your bus": "আপনার বাস খুঁজুন",
  "Live Buses": "চলমান বাস",
  "Depot Routes": "ডিপো রুট",
  "Finds a bus that is running right now. Partial numbers work too, and dashes or spaces don't matter.": "এই মুহূর্তে চলছে এমন বাস খুঁজে দেয়। আংশিক নম্বর দিলেও চলবে, ড্যাশ বা স্পেস নিয়ে চিন্তা নেই।",
  "This checks registered routes between depots, not live GPS — good for \"does a bus even run here\", any time of day.": "এটি ডিপোগুলোর মধ্যে নিবন্ধিত রুট দেখায়, লাইভ GPS নয় — \"এই পথে বাস চলে কি না\" জানতে দিনের যেকোনো সময় কাজে লাগে।",
  "Recent searches": "সাম্প্রতিক অনুসন্ধান",
  "Clear all": "সব মুছুন",
  "Routes found": "রুট পাওয়া গেছে",
  "No route registered": "কোনো রুট নিবন্ধিত নেই",
  "No route currently connects these two depots": "এই দুটি ডিপোর মধ্যে এখন কোনো রুট নেই",
  "Quick results": "দ্রুত ফলাফল",
  "Available buses": "উপলব্ধ বাস",
  "No active buses": "কোনো চলমান বাস নেই",
  "No buses are currently running on this route": "এই রুটে এখন কোনো বাস চলছে না",
  "Driver login": "চালকের লগইন",
  "Staff / Admin login": "কর্মী / অ্যাডমিন লগইন",
  "Staff or Admin login": "কর্মী বা অ্যাডমিন লগইন",
  "Toggle dark mode": "ডার্ক মোড চালু/বন্ধ",
  "From — source stop": "কোথা থেকে — শুরুর স্টপ",
  "To — destination stop": "কোথায় — গন্তব্য স্টপ",
  "Use my location (nearest stop)": "আমার অবস্থান ব্যবহার করুন (সবচেয়ে কাছের স্টপ)",
  "Use my location to find the nearest stop": "সবচেয়ে কাছের স্টপ খুঁজতে আমার অবস্থান ব্যবহার করুন",
  "Swap source and destination": "শুরু ও গন্তব্য অদলবদল করুন",
  "Bus number (e.g. WB-23A-1245)": "বাস নম্বর (যেমন WB-23A-1245)",
  "From — depot": "কোথা থেকে — ডিপো",
  "To — depot": "কোথায় — ডিপো",
  "No matching stops": "মিলছে এমন কোনো স্টপ নেই",
  "{n} active": "{n}টি চলছে",
  "{n} bus found": "{n}টি বাস পাওয়া গেছে",
  "{n} buses found": "{n}টি বাস পাওয়া গেছে",
  "{n} departure found": "{n}টি যাত্রা পাওয়া গেছে",
  "{n} departures found": "{n}টি যাত্রা পাওয়া গেছে",
  "{n} stop": "{n}টি স্টপ",
  "{n} stops": "{n}টি স্টপ",
  "Ride #{id}": "যাত্রা #{id}",
  "Track Bus": "বাস ট্র্যাক করুন",
  "{n} min": "{n} মিনিট",
  "Please select both source and destination": "অনুগ্রহ করে শুরুর ও গন্তব্য স্টপ দুটিই বাছুন",
  "Source and destination cannot be the same": "শুরু ও গন্তব্য একই হতে পারবে না",
  "Failed to fetch buses": "বাসের তথ্য আনা যায়নি",
  "Please enter a bus number": "অনুগ্রহ করে বাস নম্বর লিখুন",
  "Failed to search for the bus": "বাসটি খোঁজা যায়নি",
  "Please select both depots": "অনুগ্রহ করে দুটি ডিপোই বাছুন",
  "Source and destination depots cannot be the same": "শুরুর ও গন্তব্য ডিপো একই হতে পারবে না",
  "Failed to fetch routes": "রুটের তথ্য আনা যায়নি",
  "Finding your location…": "আপনার অবস্থান খোঁজা হচ্ছে…",
  "No stop locations are available yet.": "এখনও কোনো স্টপের অবস্থান পাওয়া যায়নি।",
  "The nearest stop is {d} away, too far to use as your boarding stop.": "সবচেয়ে কাছের স্টপ {d} দূরে, বাস ধরার স্টপ হিসেবে নেওয়ার পক্ষে অনেক দূর।",
  "Your location is approximate (about {d}), so check it.": "আপনার অবস্থান আনুমানিক (প্রায় {d}), তাই মিলিয়ে দেখুন।",
  "Nearest stop: {stop} ({d} away). Your location stays on your device.": "সবচেয়ে কাছের স্টপ: {stop} ({d} দূরে)। আপনার অবস্থান আপনার ডিভাইসেই থাকে।",
  "No running bus found for \"{q}\"": "\"{q}\" নম্বরের কোনো চলমান বাস পাওয়া যায়নি",
  "The bus may not have started its ride yet. Check the number, or search by route instead.": "বাসটি হয়তো এখনও যাত্রা শুরু করেনি। নম্বরটি আবার দেখুন, অথবা রুট দিয়ে খুঁজুন।",
  "Buses: {list}": "বাস: {list}",
  "buses run periodically": "বাস নির্দিষ্ট বিরতিতে চলে",
  "no fixed timetable registered yet": "এখনও কোনো নির্দিষ্ট সময়সূচি নিবন্ধিত নেই",
  "Loading route...": "রুট লোড হচ্ছে...",
  "☀️ Light mode": "☀️ লাইট মোড",
  "🌙 Dark mode": "🌙 ডার্ক মোড",
  "↻ Refresh now": "↻ এখনই রিফ্রেশ করুন",
  "🔗 Share bus link": "🔗 বাসের লিংক শেয়ার করুন",
  "⏰ Alarm": "⏰ অ্যালার্ম",
  "⏰ Alarm on": "⏰ অ্যালার্ম চালু",
  "📍 My location": "📍 আমার অবস্থান",
  "🎯 Find bus": "🎯 বাস খুঁজুন",
  "Arrival": "পৌঁছানো",
  "Departure": "ছাড়া",
  "Show your location to see how far the bus is from you. It stays on your device.": "বাস আপনার থেকে কত দূরে দেখতে আপনার অবস্থান দেখান। এটি আপনার ডিভাইসেই থাকে।",
  "📤 Send my location to a friend": "📤 বন্ধুকে আমার অবস্থান পাঠান",
  "Opens your phone's share menu (WhatsApp, SMS...). Only the person you pick receives it; we never see it.": "আপনার ফোনের শেয়ার মেনু খোলে (WhatsApp, SMS...)। আপনি যাকে বাছবেন শুধু সেই পাবে; আমরা এটি কখনও দেখি না।",
  "Copy message": "মেসেজ কপি করুন",
  "Waiting for bus location…": "বাসের অবস্থানের অপেক্ষায়…",
  "Waiting for bus location": "বাসের অবস্থানের অপেক্ষায়",
  "Fetching location...": "অবস্থান আনা হচ্ছে...",
  "done": "সম্পন্ন",
  "Left": "বাকি",
  "Next Stop": "পরের স্টপ",
  "Crossed": "পেরিয়েছে",
  "🔗 Share": "🔗 শেয়ার",
  "Go back": "পিছনে যান",
  "More options": "আরও বিকল্প",
  "Show or hide trip details": "যাত্রার বিবরণ দেখান বা লুকান",
  "Refresh bus location": "বাসের অবস্থান রিফ্রেশ করুন",
  "Refresh": "রিফ্রেশ",
  "No location yet": "এখনও অবস্থান নেই",
  "Couldn't refresh · showing last known": "রিফ্রেশ করা যায়নি · শেষ জানা অবস্থান দেখানো হচ্ছে",
  "just now": "এইমাত্র",
  "{t} ago": "{t} আগে",
  "{n}s": "{n} সে.",
  "{n}m": "{n} মি.",
  "{h}h {m}m": "{h} ঘ. {m} মি.",
  "{h}h": "{h} ঘ.",
  "Bus location isn't available yet": "বাসের অবস্থান এখনও পাওয়া যায়নি",
  "Track bus {bus} {route}": "বাস {bus} {route} ট্র্যাক করুন",
  "Link copied": "লিংক কপি হয়েছে",
  "Alarm set — you'll be alerted when the bus is about {n} min from your stop. Keep this page open.": "অ্যালার্ম সেট হয়েছে — বাস আপনার স্টপ থেকে প্রায় {n} মিনিট দূরে এলে জানানো হবে। এই পেজটি খোলা রাখুন।",
  "Alarm off": "অ্যালার্ম বন্ধ",
  "The bus is at {stop}": "বাস {stop}-এ পৌঁছেছে",
  "Bus reaches {stop} in about {n} min": "বাস প্রায় {n} মিনিটে {stop}-এ পৌঁছাবে",
  "{n} km/h": "{n} কিমি/ঘণ্টা",
  "Arrived {stop}": "{stop}-এ পৌঁছেছে",
  "Trip complete": "যাত্রা সম্পূর্ণ",
  "At {stop}": "{stop}-এ",
  "At a stop": "একটি স্টপে",
  "Running · next {stop}": "চলছে · পরের স্টপ {stop}",
  "On its way to your boarding stop": "আপনার ওঠার স্টপের দিকে আসছে",
  "Bus is away from the route": "বাস রুট থেকে দূরে আছে",
  "No GPS update for {t} · last known position": "{t} ধরে GPS হালনাগাদ নেই · শেষ জানা অবস্থান",
  "End of route": "রুটের শেষ",
  "No recent GPS update from the driver (last seen {t}). Showing the last known position.": "চালকের কাছ থেকে সাম্প্রতিক GPS হালনাগাদ নেই (শেষ দেখা গেছে {t})। শেষ জানা অবস্থান দেখানো হচ্ছে।",
  "Bus location is about {n} km away from this route — the GPS fix may be inaccurate or the bus is on a diversion.": "বাসের অবস্থান এই রুট থেকে প্রায় {n} কিমি দূরে — GPS ঠিক নাও হতে পারে, অথবা বাস ঘুরপথে যাচ্ছে।",
  "Showing the last position on the route.": "রুটে শেষ অবস্থান দেখানো হচ্ছে।",
  "Bus is on its way — not yet reached your boarding stop": "বাস আসছে — এখনও আপনার ওঠার স্টপে পৌঁছায়নি",
  "At stop": "স্টপে",
  "in {t}": "{t} পরে",
  "Halted {n} min": "{n} মিনিট থেমেছিল",
  "Halt {n} min": "{n} মিনিট বিরতি",
  "Your stop": "আপনার স্টপ",
  "Your dest": "আপনার গন্তব্য",
  "You are here": "আপনি এখানে",
  "at the stop": "স্টপে",
  "Based on the last known position": "শেষ জানা অবস্থানের ভিত্তিতে",
  "Estimated from the bus's recent speed and scheduled halts": "বাসের সাম্প্রতিক গতি ও নির্ধারিত বিরতি থেকে অনুমান করা",
  "You are near {stop} ({d}).": "আপনি {stop}-এর কাছে ({d})।",
  "You are {d} from the nearest stop on this route.": "এই রুটের সবচেয়ে কাছের স্টপ থেকে আপনি {d} দূরে।",
  "The bus is about {d} from you (straight line).": "বাস আপনার থেকে প্রায় {d} দূরে (সরলরেখায়)।",
  "Waiting for the bus's location…": "বাসের অবস্থানের অপেক্ষায়…",
  "Copied. Paste it into any chat.": "কপি হয়েছে। যেকোনো চ্যাটে পেস্ট করুন।",
  "Copy this message:": "এই মেসেজটি কপি করুন:",
  "My location": "আমার অবস্থান",
  "Sent.": "পাঠানো হয়েছে।",
  "Couldn't share your location. Please try again.": "আপনার অবস্থান শেয়ার করা যায়নি। আবার চেষ্টা করুন।",
  "Could not load stops": "স্টপ লোড করা যায়নি",
  "{n} m": "{n} মি.",
  "{n} km": "{n} কিমি",
  "This browser can't share its location.": "এই ব্রাউজার অবস্থান শেয়ার করতে পারে না।",
  "Location only works on a secure (https) connection.": "অবস্থান শুধু নিরাপদ (https) সংযোগে কাজ করে।",
  "Location is turned off for this site. Allow it in your browser (lock icon next to the address, then Location) and tap again.": "এই সাইটের জন্য অবস্থান বন্ধ আছে। ব্রাউজারে অনুমতি দিন (ঠিকানার পাশের তালার চিহ্ন, তারপর Location) এবং আবার ট্যাপ করুন।",
  "Your location couldn't be found. Check that location/GPS is turned on, then try again.": "আপনার অবস্থান পাওয়া যায়নি। লোকেশন/GPS চালু আছে কি না দেখে আবার চেষ্টা করুন।",
  "Finding your location took too long. Try again, ideally with a clear view of the sky.": "অবস্থান খুঁজতে অনেক সময় লাগছে। আকাশ দেখা যায় এমন খোলা জায়গায় গিয়ে আবার চেষ্টা করুন।",
  "Couldn't get your location. Please try again.": "আপনার অবস্থান পাওয়া যায়নি। আবার চেষ্টা করুন।",
  "My location: {url}": "আমার অবস্থান: {url}",
  "Near {stop} ({d} away).": "{stop}-এর কাছে ({d} দূরে)।",
  "(Approximate location, about {d}.)": "(আনুমানিক অবস্থান, প্রায় {d}।)",
  "The bus is about {d} from me.": "বাস আমার থেকে প্রায় {d} দূরে।",
  "Track the bus live: {url}": "বাস লাইভ ট্র্যাক করুন: {url}",
  "Admin Login": "অ্যাডমিন লগইন",
  "Where Is My Bus — Admin Panel": "আমার বাস কোথায় — অ্যাডমিন প্যানেল",
  "❌ Invalid username or password": "❌ ব্যবহারকারীর নাম বা পাসওয়ার্ড ভুল",
  "✅ Logged out successfully": "✅ সফলভাবে লগ আউট হয়েছে",
  "Username": "ব্যবহারকারীর নাম",
  "Password": "পাসওয়ার্ড",
  "Sign In →": "সাইন ইন →",
  "Passenger?": "যাত্রী?",
  "Go to search →": "খোঁজার পেজে যান →",
  "Enter admin username": "অ্যাডমিনের ব্যবহারকারীর নাম লিখুন",
  "Enter password": "পাসওয়ার্ড লিখুন",
  "Driver Panel": "চালক প্যানেল",
  "Your Depot": "আপনার ডিপো",
  "Loading depots...": "ডিপো লোড হচ্ছে...",
  "Depot Code": "ডিপো কোড",
  "Ask your depot office for this code. You need it every time you log in.": "এই কোডটি ডিপো অফিস থেকে চেয়ে নিন। প্রতিবার লগইনের সময় এটি লাগবে।",
  "Continue →": "এগিয়ে যান →",
  "Loading routes...": "রুট লোড হচ্ছে...",
  "Start Location": "শুরুর স্থান",
  "Pick a route first": "আগে একটি রুট বাছুন",
  "🚦 Start Ride": "🚦 যাত্রা শুরু করুন",
  "Ride is Active": "যাত্রা চলছে",
  "Ride ID": "যাত্রা আইডি",
  "Current stop position": "বর্তমান স্টপের অবস্থান",
  "📡 Live": "📡 লাইভ",
  "Waiting for location…": "অবস্থানের অপেক্ষায়…",
  "📡 Live GPS": "📡 লাইভ GPS",
  "🧪 Simulate location": "🧪 অবস্থান সিমুলেট করুন",
  "Test mode: pick a stop, or slide toward the next one. This position is sent as the bus location and real GPS is ignored until you switch back to": "টেস্ট মোড: একটি স্টপ বাছুন, বা পরেরটির দিকে স্লাইড করুন। এই অবস্থানটিই বাসের অবস্থান হিসেবে পাঠানো হয় এবং আসল GPS উপেক্ষা করা হয়, যতক্ষণ না আপনি ফিরে যান",
  "Live GPS": "লাইভ GPS",
  "Bus is at / just after stop": "বাস স্টপে / স্টপের ঠিক পরে",
  "⏮ Prev stop": "⏮ আগের স্টপ",
  "Next stop ⏭": "পরের স্টপ ⏭",
  "Sending": "পাঠানো হচ্ছে",
  "📍 Send this location now": "📍 এই অবস্থান এখনই পাঠান",
  "🛑 Stop Ride": "🛑 যাত্রা শেষ করুন",
  "Enter Bus Number (WB-23A-1245)": "বাস নম্বর লিখুন (WB-23A-1245)",
  "Code for your depot": "আপনার ডিপোর কোড",
  "Show driver code": "চালকের কোড দেখান",
  "Hide driver code": "চালকের কোড লুকান",
  "Last pause: {t} at {time}": "শেষ বিরতি: {t}, {time}-এ",
  "Screen was off for {t}: GPS restarted. Keep the screen on for continuous tracking.": "স্ক্রিন {t} বন্ধ ছিল: GPS আবার চালু হয়েছে। নিরবচ্ছিন্ন ট্র্যাকিংয়ের জন্য স্ক্রিন চালু রাখুন।",
  "No new GPS fix for {t}: passengers see your last known position": "{t} ধরে নতুন GPS পাওয়া যায়নি: যাত্রীরা আপনার শেষ জানা অবস্থান দেখছেন",
  "Background tracking is on. Location keeps sending with the screen off (see the app notification).": "ব্যাকগ্রাউন্ড ট্র্যাকিং চালু আছে। স্ক্রিন বন্ধ থাকলেও অবস্থান পাঠানো চলবে (অ্যাপের নোটিফিকেশন দেখুন)।",
  "Keep this screen on and this page open. Browsers pause GPS when the phone locks or you switch apps.": "এই স্ক্রিন চালু ও এই পেজ খোলা রাখুন। ফোন লক হলে বা অন্য অ্যাপে গেলে ব্রাউজার GPS থামিয়ে দেয়।",
  "For tracking with the screen off, use the WIMB Driver app.": "স্ক্রিন বন্ধ রেখে ট্র্যাকিংয়ের জন্য WIMB Driver অ্যাপ ব্যবহার করুন।",
  "Select your depot": "আপনার ডিপো বাছুন",
  "Could not load depots": "ডিপো লোড করা যায়নি",
  "Select your route": "আপনার রুট বাছুন",
  "No routes available": "কোনো রুট নেই",
  "Could not load routes": "রুট লোড করা যায়নি",
  "Select start stop": "শুরুর স্টপ বাছুন",
  "No stops on this route": "এই রুটে কোনো স্টপ নেই",
  "Select destination stop": "গন্তব্য স্টপ বাছুন",
  "Please select a route first": "অনুগ্রহ করে আগে একটি রুট বাছুন",
  "Please fill in Bus Number, Source, and Destination": "অনুগ্রহ করে বাস নম্বর, শুরু ও গন্তব্য পূরণ করুন",
  "Source and destination must be different": "শুরু ও গন্তব্য আলাদা হতে হবে",
  "Fetching start location...": "শুরুর অবস্থান আনা হচ্ছে...",
  "Stop not in route data, trying GPS...": "স্টপটি রুটের তথ্যে নেই, GPS চেষ্টা করা হচ্ছে...",
  "Could not get location:": "অবস্থান পাওয়া যায়নি:",
  "Starting ride...": "যাত্রা শুরু হচ্ছে...",
  "Ride Started ✅ (ID: {id})": "যাত্রা শুরু হয়েছে ✅ (আইডি: {id})",
  "This device/browser does not support GPS, so live tracking cannot start.": "এই ডিভাইস/ব্রাউজার GPS সমর্থন করে না, তাই লাইভ ট্র্যাকিং শুরু করা যাবে না।",
  "GPS problem:": "GPS সমস্যা:",
  "check location permission": "লোকেশনের অনুমতি দেখুন",
  "Session expired or ride ended — passengers can't see you. Stop the ride and start again.": "সেশন শেষ বা যাত্রা সমাপ্ত — যাত্রীরা আপনাকে দেখতে পাচ্ছেন না। যাত্রা বন্ধ করে আবার শুরু করুন।",
  "Server error {n} while sending location — retrying": "অবস্থান পাঠানোর সময় সার্ভার ত্রুটি {n} — আবার চেষ্টা চলছে",
  "GPS ±{n} m": "GPS ±{n} মি.",
  "Sending SIMULATED location — last sent {t}": "সিমুলেটেড অবস্থান পাঠানো হচ্ছে — শেষ পাঠানো {t}",
  "last sent {t}": "শেষ পাঠানো {t}",
  "Sharing live location — last sent {t}": "লাইভ অবস্থান শেয়ার হচ্ছে — শেষ পাঠানো {t}",
  "No internet — location not reaching passengers, retrying…": "ইন্টারনেট নেই — অবস্থান যাত্রীদের কাছে পৌঁছাচ্ছে না, আবার চেষ্টা চলছে…",
  "Back on live GPS — waiting for the next fix…": "আবার লাইভ GPS-এ ফিরেছে — পরবর্তী ফিক্সের অপেক্ষায়…",
  "Simulated": "সিমুলেটেড",
  "Loading route…": "রুট লোড হচ্ছে…",
  "Stop {i} of {n}": "স্টপ {i} / {n}",
  "next: {stop}": "পরের: {stop}",
  "end of route": "রুটের শেষ",
  "(end of route)": "(রুটের শেষ)",
  "{p}% of the way": "পথের {p}% পেরিয়েছে",
  "Near {stop}": "{stop}-এর কাছে",
  "{d} away": "{d} দূরে",
  "Ride Stopped ⛔": "যাত্রা বন্ধ হয়েছে ⛔",
  "Checking...": "যাচাই করা হচ্ছে...",
  "Please select your depot": "অনুগ্রহ করে আপনার ডিপো বাছুন",
  "Please enter your depot code": "অনুগ্রহ করে আপনার ডিপো কোড লিখুন",
  "Invalid depot or code": "ডিপো বা কোড ভুল",
  "Ride Resumed ✅": "যাত্রা আবার চালু হয়েছে ✅",
  "Track Bus | WIMB": "বাস ট্র্যাক | WIMB",
  "Admin Login | WIMB": "অ্যাডমিন লগইন | WIMB",
  "Driver Panel | WIMB": "চালক প্যানেল | WIMB",
  "Today - {date}": "আজ - {date}"
};

  function detect() {
    try {
      var saved = localStorage.getItem(STORE);
      if (saved === "en" || saved === "bn") return saved;
    } catch (_) {}
    var nav = (navigator.language || "").toLowerCase();
    return nav.indexOf("bn") === 0 ? "bn" : "en";
  }
  var lang = detect();

  function tr(key, params) {
    var s = (lang === "bn" && Object.prototype.hasOwnProperty.call(BN, key)) ? BN[key] : key;
    if (params) {
      s = s.replace(/\{(\w+)\}/g, function (m, k) { return params[k] != null ? params[k] : m; });
    }
    return s;
  }

  // Locale for dates/times. "-u-nu-latn" keeps 0-9 digits so numbers look the same everywhere.
  function locale() { return lang === "bn" ? ["bn-IN-u-nu-latn"] : []; }

  function setLang(next) {
    if (next !== "en" && next !== "bn") return;
    try { localStorage.setItem(STORE, next); } catch (_) {}
    window.location.reload();
  }

  /* ── translate the static page ── */
  function norm(s) { return s.replace(/\s+/g, " ").trim(); }

  function translateTextNode(node) {
    var raw = node.nodeValue;
    var key = norm(raw);
    if (!key || !Object.prototype.hasOwnProperty.call(BN, key)) return;
    var lead = raw.match(/^\s*/)[0], trail = raw.match(/\s*$/)[0];
    node.nodeValue = lead + BN[key] + trail;
  }

  function translateAttrs(el) {
    ["placeholder", "title", "aria-label", "alt"].forEach(function (a) {
      var v = el.getAttribute && el.getAttribute(a);
      if (v && Object.prototype.hasOwnProperty.call(BN, norm(v))) el.setAttribute(a, BN[norm(v)]);
    });
  }

  function walk(root) {
    if (root.nodeType === 1) {
      var tag = root.tagName;
      if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT") return;
      if (root.hasAttribute("data-no-i18n") || root.hasAttribute("data-lang-toggle")) return;
      translateAttrs(root);
    }
    for (var c = root.firstChild; c; c = c.nextSibling) {
      if (c.nodeType === 3) translateTextNode(c);
      else if (c.nodeType === 1) walk(c);
    }
  }

  function setupToggle() {
    var els = document.querySelectorAll("[data-lang-toggle]");
    for (var i = 0; i < els.length; i++) {
      (function (el) {
        el.textContent = lang === "bn" ? "EN" : "বাং";
        el.setAttribute("title", lang === "bn" ? "Switch to English" : "বাংলায় দেখুন");
        el.setAttribute("aria-label", lang === "bn" ? "Switch to English" : "বাংলায় দেখুন");
        el.addEventListener("click", function (e) {
          e.preventDefault(); e.stopPropagation();
          setLang(lang === "bn" ? "en" : "bn");
        });
      })(els[i]);
    }
  }

  function loadBengaliFont() {
    var link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = "https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;500;600;700&display=swap";
    document.head.appendChild(link);
    var st = document.createElement("style");
    st.textContent =
      "html[lang='bn'] body, html[lang='bn'] button, html[lang='bn'] input, html[lang='bn'] select," +
      "html[lang='bn'] textarea{font-family:'Noto Sans Bengali','Hind Siliguri',system-ui,-apple-system,'Segoe UI',sans-serif}" +
      "html[lang='bn'] body{line-height:1.55}";
    document.head.appendChild(st);
  }

  document.documentElement.setAttribute("lang", lang);
  if (lang === "bn") loadBengaliFont();

  function init() {
    if (lang === "bn") {
      walk(document.body);
      var tkey = norm(document.title || "");
      if (Object.prototype.hasOwnProperty.call(BN, tkey)) document.title = BN[tkey];
    }
    setupToggle();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();

  window.tr = tr;
  window.I18N = { lang: function () { return lang; }, setLang: setLang, toggle: function () { setLang(lang === "bn" ? "en" : "bn"); }, locale: locale, apply: walk };
})();
