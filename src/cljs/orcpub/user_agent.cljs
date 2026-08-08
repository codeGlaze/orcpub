(ns orcpub.user-agent
  "Browser, device, and platform detection utilities.
   Uses native JS navigator.userAgent for compatibility across Closure Library versions."
  (:require [goog.labs.userAgent.device :as g-device]
            [goog.labs.userAgent.platform :as g-platform]
            [clojure.string :as str]))

(defn- user-agent-string []
  (when (exists? js/navigator)
    (.-userAgent js/navigator)))

(defn browser
  "Detects the current browser. Returns a keyword like :chrome, :firefox, :safari, etc."
  []
  (let [ua (str/lower-case (or (user-agent-string) ""))]
    (cond
      (str/includes? ua "edg") :edge        ; Edge uses "Edg" in UA
      (str/includes? ua "chrome") :chrome   ; Must check after Edge
      (str/includes? ua "firefox") :firefox
      (str/includes? ua "safari") :safari   ; Must check after Chrome
      (str/includes? ua "opera") :opera
      (or (str/includes? ua "msie") (str/includes? ua "trident")) :ie
      :else :not-found)))

(defn browser-version []
  ;; Return empty string - version detection is complex and rarely needed
  "")

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
