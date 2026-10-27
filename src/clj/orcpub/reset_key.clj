(ns orcpub.reset-key
  "The digest a password-reset key is stored as. Its own namespace because two
   places write reset keys -- the reset form (orcpub.routes) and the artist
   welcome link (orcpub.artist-accounts) -- and routes already depends on artist
   accounts. Both must store the same digest, or a link one wrote is a link the
   other cannot find.")

(defn digest
  "What goes in the database. The emailed key is the secret; storing it verbatim
   made anyone who can read the user table able to complete a reset on any
   account with one outstanding. Only the digest is kept, so a stolen table
   yields nothing that can be mailed back in."
  [key]
  (->> (.getBytes ^String key "UTF-8")
       (.digest (java.security.MessageDigest/getInstance "SHA-256"))
       (map #(format "%02x" %))
       (apply str)))
