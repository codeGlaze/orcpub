(ns orcpub.user-agent
  "Browser, device, and platform detection utilities.
   
   ## Google Closure Library Migration (ClojureScript 1.11.x)
   
   The goog.labs.userAgent.browser API changed in newer Closure versions:
   - OLD: (g-browser/isChrome), (g-browser/isFirefox), etc.
   - NEW: (g-browser/matchBrowser \"Chrome\"), or use Brand enum
   
   We use matchBrowser for compatibility with the updated Closure library."
  (:require [goog.labs.userAgent.browser :as g-browser]
            [goog.labs.userAgent.device :as g-device]
            [goog.labs.userAgent.platform :as g-platform]))

(defn browser
  "Detects the current browser. Returns a keyword like :chrome, :firefox, :safari, etc.
   Uses goog.labs.userAgent.browser/matchBrowser for modern Closure compatibility."
  []
  (cond
    (g-browser/matchBrowser "Chromium") :chrome  ; Covers Chrome, Chromium-based Edge, etc.
    (g-browser/matchBrowser "Firefox") :firefox
    (g-browser/matchBrowser "Safari") :safari
    (g-browser/matchBrowser "Edge") :edge
    (g-browser/matchBrowser "IE") :ie
    :else :not-found))

(defn browser-version []
  (g-browser/getVersion))

(defn device-type []
  (cond
    (g-device/isDesktop) :desktop
    (g-device/isMobile) :mobile
    (g-device/isTablet) :tablet
    :else :not-found))

(defn platform []
  (cond
    (g-platform/isAndroid) :android
    (g-platform/isChromeOS) :chrome-os
    (g-platform/isIos) :ios
    (g-platform/isIpad) :ipad
    (g-platform/isIphone) :iphone
    (g-platform/isIpod) :ipod
    (g-platform/isLinux) :linux
    (g-platform/isMacintosh) :macintosh
    (g-platform/isWindows) :windows
    :else :not-found))

(defn platform-version []
  (g-platform/getVersion))
