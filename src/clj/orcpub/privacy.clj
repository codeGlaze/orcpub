(ns orcpub.privacy
  (:require [hiccup.page :as page]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.integrations :as integrations]
            [orcpub.fork.privacy-content :as content]
            [environ.core :as environ]))

(defn section [{:keys [title font-size paragraphs subsections]}]
  [:div
   [:div.m-t-20.f-w-b
    {:style (str "color:#2c3445;font-size:" font-size "px")}
    title]
   (map
    (fn [p] [:p p])
    paragraphs)
   (map
    section
    subsections)])

(def privacy-policy-section
  "Privacy policy content — fork-specific. See fork/privacy_content.clj."
  content/privacy-policy-section)

(defn terms-page [sections]
  (page/html5
   [:head
    [:link {:rel :stylesheet :href "/css/style.css" :type "text/css"}]
    [:link {:rel :stylesheet :href "/css/compiled/styles.css" :type "text/css"}]

    ;; Third-party integration tags (analytics, ads) — same as index.clj.
    ;; Empty on public repo, populated on DMV via integrations.clj.
    (integrations/head-tags nil)]
   [:body.sans
    [:div
     [:div.app-header-bar.container
      {:style "background-color:#2c3445"}
      [:div.content
       [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20
        [:a {:href "/" } [:img.h-72.pointer {:src branding/logo-path}]]]]]
     [:div.container
      [:div.content
       [:div.f-s-24
        (section sections)]]]]]))

(defn privacy-policy []
  (terms-page privacy-policy-section))

(def terms-section
  {:title "Terms of Service"
   :font-size 48
   :subsections
   [{:title (str "Thank you for using " branding/app-name "!")
     :font-size 32
     :paragraphs
     [[:div (str "These Terms of Service (\"Terms\") govern your access to and use of " branding/app-name "'s website, products, and services (\"Products\"). Please read these Terms carefully, and contact us if you have any questions. By accessing or using our Products, you agree to be bound by these Terms and by our ") [:a {:href "/privacy-policy" :target :_blank} "Privacy Policy"] ". You also confirm you have read and agreed to our " [:a {:href "/community-guidelines" :target :_blank} "Community guidelines"] " and our " [:a {:href "/cookies-policy"} "Cookies policy"] "."]]}
    {:title (str "1. Using " branding/app-name)
     :font-size 32
     :subsections
     [{:title (str "a. Who can use " branding/app-name)
       :font-size 28
       :paragraphs
       [(str "You may use our Products only if you can form a binding contract with " branding/app-name ", and only in compliance with these Terms and all applicable laws. When you create your " branding/app-name " account, you must provide us with accurate and complete information. Any use or access by anyone under the age of 13 is prohibited. If you open an account on behalf of a company, organization, or other entity, then (a) \"you\" includes you and that entity, and (b) you represent and warrant that you are authorized to grant all permissions and licenses provided in these Terms and bind the entity to these Terms, and that you agree to these Terms on the entity's behalf. Some of our Products may be software that is downloaded to your computer, phone, tablet, or other device. You agree that we may automatically upgrade those Products, and these Terms will apply to such upgrades.")]}
      {:title "b. Our license to you"
       :font-size 28
       :paragraphs
       ["Subject to these Terms and our policies (including our Community guidelines), we grant you a limited, non-exclusive, non-transferable, and revocable license to use our Products."]}]}
    {:title "2. Your content"
     :font-size 32
     :subsections
     [{:title "a. Posting Content"
       :font-size 28
       :paragraphs
       [(str branding/app-name " allows you to post content, including photos, comments, links, and other materials. Anything that you post or otherwise make available on our Products is referred to as \"User Content.\" You retain all rights in, and are solely responsible for, the User Content you post to " branding/app-name ".")]}
      {:title (str "b. How " branding/app-name " and other users can use your content")
       :font-size 28
       :paragraphs
       [(str "You grant " branding/app-name " and our users a non-exclusive, royalty-free, transferable, sublicensable, worldwide license to use, store, display, reproduce, save, modify, create derivative works, perform, and distribute your User Content on " branding/app-name " solely for the purposes of operating, developing, providing, and using the " branding/app-name " Products. Nothing in these Terms shall restrict other legal rights " branding/app-name " may have to User Content, for example under other licenses. We reserve the right to remove or modify User Content for any reason, including User Content that we believe violates these Terms or our policies.")]}
      {:title "c. How long we keep your content"
       :font-size 28
       :paragraphs
       [(str "Following termination or deactivation of your account, or if you remove any User Content from " branding/app-name ", we may retain your User Content for a commercially reasonable period of time for backup, archival, or audit purposes. Furthermore, " branding/app-name " and its users may retain and continue to use, store, display, reproduce, modify, create derivative works, perform, and distribute any of your User Content that other users have stored or shared through " branding/app-name ".")]}
      {:title "d. Feedback you provide"
       :font-size 28
       :paragraphs
       [(str "We value hearing from our users, and are always interested in learning about ways we can make " branding/app-name " more awesome. If you choose to submit comments, ideas or feedback, you agree that we are free to use them without any restriction or compensation to you. By accepting your submission, " branding/app-name " does not waive any rights to use similar or related Feedback previously known to " branding/app-name ", or developed by its employees, or obtained from sources other than you")]}]}
    {:title "3. Security"
     :font-size 32
     :paragraphs
     [(str "We care about the security of our users. While we work to protect the security of your content and account, " branding/app-name " cannot guarantee that unauthorized third parties will not be able to defeat our security measures. We ask that you keep your password secure. Please notify us immediately of any compromise or unauthorized use of your account.")]}
    {:title "4. Third-party links, sites, and services"
     :font-size 32
     :paragraphs
     [(str "Our Products may contain links to third-party websites, advertisers, services, special offers, or other events or activities that are not owned or controlled by " branding/app-name ". We do not endorse or assume any responsibility for any such third-party sites, information, materials, products, or services. If you access any third party website, service, or content from " branding/app-name ", you do so at your own risk and you agree that " branding/app-name " will have no liability arising from your use of or access to any third-party website, service, or content.")]}
    {:title "5. Termination"
     :font-size 32
     :paragraphs
     [(str branding/app-name " may terminate or suspend this license at any time, with or without cause or notice to you. Upon termination, you continue to be bound by Sections 2 and 6-11 of these Terms.")]}
    {:title "6. Indemnity"
     :font-size 32
     :paragraphs
     [(str "If you use our Products for commercial purposes without agreeing to our Business Terms as required by Section 1, as determined in our sole and absolute discretion, you agree to indemnify and hold harmless " branding/app-name " and its respective officers, directors, employees and agents, from and against any claims, suits, proceedings, disputes, demands, liabilities, damages, losses, costs and expenses, including, without limitation, reasonable legal and accounting fees (including costs of defense of claims, suits or proceedings brought by third parties), in any way related to (a) your access to or use of our Products, (b) your User Content, or (c) your breach of any of these Terms.")]}
    {:title "7. Disclaimers"
     :font-size 32
     :paragraphs
     ["The Products and all included content are provided on an \"as is\" basis without warranty of any kind, whether express or implied."
      (str branding/app-name " SPECIFICALLY DISCLAIMS ANY AND ALL WARRANTIES AND CONDITIONS OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT, AND ANY WARRANTIES ARISING OUT OF COURSE OF DEALING OR USAGE OF TRADE.")
      (str branding/app-name " takes no responsibility and assumes no liability for any User Content that you or any other user or third party posts or transmits using our Products. You understand and agree that you may be exposed to User Content that is inaccurate, objectionable, inappropriate for children, or otherwise unsuited to your purpose.")]}
    {:title "8. Limitation of liability"
     :font-size 32
     :paragraphs
     [(str "TO THE MAXIMUM EXTENT PERMITTED BY LAW, " branding/app-name " SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL OR PUNITIVE DAMAGES, OR ANY LOSS OF PROFITS OR REVENUES, WHETHER INCURRED DIRECTLY OR INDIRECTLY, OR ANY LOSS OF DATA, USE, GOOD-WILL, OR OTHER INTANGIBLE LOSSES, RESULTING FROM (A) YOUR ACCESS TO OR USE OF OR INABILITY TO ACCESS OR USE THE PRODUCTS; (B) ANY CONDUCT OR CONTENT OF ANY THIRD PARTY ON THE PRODUCTS, INCLUDING WITHOUT LIMITATION, ANY DEFAMATORY, OFFENSIVE OR ILLEGAL CONDUCT OF OTHER USERS OR THIRD PARTIES; OR (C) UNAUTHORIZED ACCESS, USE OR ALTERATION OF YOUR TRANSMISSIONS OR CONTENT. IN NO EVENT SHALL " branding/app-name "'s AGGREGATE LIABILITY FOR ALL CLAIMS RELATING TO THE PRODUCTS EXCEED ONE HUNDRED U.S. DOLLARS (U.S. $100.00).")]}
    {:title "9. Arbitration"
     :font-size 32
     :paragraphs
     [(str "For any dispute you have with " branding/app-name ", you agree to first contact us and attempt to resolve the dispute with us informally. If " branding/app-name " has not been able to resolve the dispute with you informally, we each agree to resolve any claim, dispute, or controversy (excluding claims for injunctive or other equitable relief) arising out of or in connection with or relating to these Terms by binding arbitration by the American Arbitration Association (\"AAA\") under the Commercial Arbitration Rules and Supplementary Procedures for Consumer Related Disputes then in effect for the AAA, except as provided herein. Unless you and " branding/app-name " agree otherwise, the arbitration will be conducted in Tulsa County, Oklahoma or the United States District Court for the District of Oklahoma with in the United states. Each party will be responsible for paying any AAA filing, administrative and arbitrator fees in accordance with AAA rules, except that " branding/app-name " will pay for your reasonable filing, administrative, and arbitrator fees if your claim for damages does not exceed $75,000 and is non-frivolous (as measured by the standards set forth in Federal Rule of Civil Procedure 11(b)). The award rendered by the arbitrator shall include costs of arbitration, reasonable attorneys' fees and reasonable costs for expert and other witnesses, and any judgment on the award rendered by the arbitrator may be entered in any court of competent jurisdiction. Nothing in this Section shall prevent either party from seeking injunctive or other equitable relief from the courts for matters related to data security, intellectual property or unauthorized access to the Service. ALL CLAIMS MUST BE BROUGHT IN THE PARTIES' INDIVIDUAL CAPACITY, AND NOT AS A PLAINTIFF OR CLASS MEMBER IN ANY PURPORTED CLASS OR REPRESENTATIVE PROCEEDING, AND, UNLESS WE AGREE OTHERWISE, THE ARBITRATOR MAY NOT CONSOLIDATE MORE THAN ONE PERSON'S CLAIMS. YOU AGREE THAT, BY ENTERING INTO THESE TERMS, YOU AND " branding/app-name " ARE EACH WAIVING THE RIGHT TO A TRIAL BY JURY OR TO PARTICIPATE IN A CLASS ACTION.")
      (str "To the extent any claim, dispute or controversy regarding " branding/app-name " or our Products isn't arbitrable under applicable laws or otherwise: you and " branding/app-name " both agree that any claim or dispute regarding " branding/app-name " will be resolved exclusively in accordance with Clause 10 of these Terms.")]}
    {:title "10. Governing law and jurisdiction"
     :font-size 32
     :paragraphs
     ["These Terms shall be governed by the laws of the State of Oklahoma, without respect to its conflict of laws principles. We each agree to submit to the personal jurisdiction of a state court located in Tulsa County, Oklahoma or the United States District Court for the District of Oklahoma, for any actions not subject to Section 9 (Arbitration)."]}
    {:title "11. General terms"
     :font-size 32
     :subsections
     [{:title "Notification procedures and changes to these Terms"
       :font-size 28
       :paragraphs
       [(str branding/app-name " reserves the right to determine the form and means of providing notifications to you, and you agree to receive legal notices electronically if we so choose. We may revise these Terms from time to time and the most current version will always be posted on our website. If a revision, in our sole discretion, is material we will notify you. By continuing to access or use the Products after revisions become effective, you agree to be bound by the revised Terms. If you do not agree to the new terms, please stop using the Products.")]}
      {:title "Assignment"
       :font-size 28
       :paragraphs
       [(str "These Terms, and any rights and licenses granted hereunder, may not be transferred or assigned by you, but may be assigned by " branding/app-name " without restriction. Any attempted transfer or assignment in violation hereof shall be null and void.")]}
      {:title "Entire agreement/severability"
       :font-size 28
       :paragraphs
       [(str "These Terms, together with the Privacy policy and any amendments and any additional agreements you may enter into with " branding/app-name " in connection with the Products, shall constitute the entire agreement between you and " branding/app-name " concerning the Products. If any provision of these Terms is deemed invalid, then that provision will be limited or eliminated to the minimum extent necessary, and the remaining provisions of these Terms will remain in full force and effect.")]}
      {:title "No waiver"
       :font-size 28
       :paragraphs
       [(str "No waiver of any term of these Terms shall be deemed a further or continuing waiver of such term or any other term, and " branding/app-name "'s failure to assert any right or provision under these Terms shall not constitute a waiver of such right or provision.")]}
      {:title "Terms of reuse"
       :font-size 28
       :paragraphs
       ["Code and portions of this Derivative Works are used under Eclipse Public License 2.0 https://github.com/Orcpub/orcpub/blob/develop/LICENSE"]}
      {:title "Parties"
       :font-size 28
       :paragraphs
       [(str "These Terms are a contract between you and " branding/app-name)
        "Effective Dec 28th, 2021"]}]}]})

(defn terms-of-use []
  (terms-page terms-section))

(def community-guidelines-section
  {:title "Community guidelines"
   :font-size 48
   :subsections
   [{:title "Our Mission"
     :font-size 32
     :paragraphs
     [(str "At " branding/app-name ", our mission is to help you discover and do what you love. That means showing you ideas that are relevant, interesting and personal to you, and making sure you don't see anything that's inappropriate or spammy.")
      (str "These are guidelines for what we do and don't allow on " branding/app-name ". If you come across content that seems to break these rules, you can report it to us.")]}
    {:title "Safety"
     :font-size 32
     :paragraphs
     ["We remove porn. We may hide nudity or erotica."
      "We remove content that physically or sexually exploits people. We work with law enforcement to address the sexualization of minors."
      "We remove images that show gratuitous violence or glorify violence."
      "We remove anything that promotes self-harm, like self mutilation, eating disorders or drug abuse."
      "We remove hate speech and discrimination, or groups and people that advocate either."
      "We remove content used to threaten or organize violence or support violent organizations."
      "We remove attacks on private people or sharing of personally identifiable information."
      "We remove content used to sell or buy regulated goods, like drugs, alcohol, tobacco, firearms and other hazardous materials."
      "We remove accounts that impersonate any person or organization."]}
    {:title "Intellectual property and other rights"
     :font-size 32
     :paragraphs
     [(str "To respect the rights of people on and off " branding/app-name ", please:")
      "Don't infringe anyone's intellectual property, privacy or other rights."
      "Don't do anything or post any content that violates laws or regulations."
      (str "Don't use " branding/app-name "'s name, logo or trademark in a way that confuses people.")]}
    {:title "Site security and access"
     :font-size 32
     :paragraphs
     [(str "To keep " branding/app-name " secure, we ask that you please:")
      "Don't access, use or tamper with our systems or our technical providers' systems."
      "Don't break or circumvent our security measures or test the vulnerability of our systems or networks."
      (str "Don't use any undocumented or unsupported method to access, search, scrape, download or change any part of " branding/app-name ".")
      "Don't try to reverse engineer our software."
      (str "Don't try to interfere with people on " branding/app-name " or our hosts or networks, like sending a virus, overloading, spamming or mail-bombing.")
      (str "Don't collect or store personally identifiable information from " branding/app-name " or people on " branding/app-name " without permission.")
      "Don't share your password, let anyone access your account or do anything that might put your account at risk."
      "Don't sell access to your account, boards, or username, or otherwise transfer account features for compensation."]}
    {:title "Spam"
     :font-size 32
     :paragraphs
     ["Nobody likes spam or other disruptive content. Which is why we remove accounts for stuff like:"
      "Unsolicited commercial messages."
      "Attempts to artificially boost views and other metrics."
      "Repetitive or unwanted posts."
      "Off-domain redirects, cloaking or other ways of obscuring where content leads."
      "Misleading content."
      "Effective Nov 4th, 2020"]}]})

(defn community-guidelines []
  (terms-page community-guidelines-section))

(def cookie-policy-section
  {:title "Cookies"
   :font-size 48
   :subsections
   [{:title (str "Cookies on " branding/app-name)
     :font-size 32
     :paragraphs
     [(str "Our privacy policy describes how we collect and use information, and what choices you have. One way we collect information is through the use of a technology called \"cookies.\" We use cookies for all kinds of things on " branding/app-name ".")]}
    {:title "What's a cookie?"
     :font-size 32
     :paragraphs
     ["When you go online, you use a program called a \"browser\" (like Apple's Safari or Google's Chrome). Most websites store a small amount of text in the browser and that text is called a \"cookie.\""]}
    {:title "How we use cookies"
     :font-size 32
     :paragraphs
     [(str "We use cookies for lots of essential things on " branding/app-name " like helping you log in and tailoring your " branding/app-name " experience. Here are some specifics on how we use cookies.")]}
    {:title "What we use cookies for"
     :font-size 32
     :subsections
     [{:title "Personalization"
       :font-size 28
       :paragraphs
       ["Cookies help us remember which content, boards, people or websites you've interacted with so we can show you related content you might like."
        "We also use cookies to help advertisers show you interesting ads."]}
      {:title "Preferences"
       :font-size 28
       :paragraphs
       ["We use cookies to remember your settings and preferences, like the language you prefer and your privacy settings."]}
      {:title "Login"
       :font-size 32
       :paragraphs
       [(str "Cookies let you log in and out of " branding/app-name ".")]}
      {:title "Security"
       :font-size 32
       :paragraphs
       [(str "Cookies are just one way we protect you from security risks. For example, we use them to detect when someone might be trying to hack your " branding/app-name " account or spam the " branding/app-name " community.")]}
      {:title "Analytics"
       :font-size 32
       :paragraphs
       [(str "We use cookies to make " branding/app-name " better. For example, these cookies tell us how many people use a certain feature and how popular it is, or whether people open an email we send.")
        "We also use cookies to help advertisers understand who sees and interacts with their ads, and who visits their website or purchases their products."]}
      {:title "Service providers"
       :font-size 32
       :paragraphs
       [(str "Sometimes we hire security vendors or use third-party analytics providers to help us understand how people are using " branding/app-name ". Just like we do, these providers may use cookies. Learn more about the third party providers we use.")]}]}
    {:title "Where we use cookies"
     :font-size 32
     :paragraphs
     [(str "We use cookies on " branding/app-name ", in our mobile applications, and in our products and services (like ads, emails and applications). We also use them on the websites of partners who use " branding/app-name "'s Save button, " branding/app-name " widgets, or ad tools like conversion tracking.")]}
    {:title "Your options"
     :font-size 32
     :paragraphs
     ["Your browser probably gives you cookie choices. For example, most browsers let you block \"third party cookies,\" which are cookies from sites other than the one you're visiting. Those options vary from browser to browser, so check your browser settings for more info."
      (str "Some browsers also have a privacy setting called \"Do Not Track,\" which we support. This setting is another way for you to decide whether we use info from our partners and other services to customize " branding/app-name " for you.")
      "Effective Nov 4th, 2020"]}]})

(defn cookie-policy []
  (terms-page cookie-policy-section))
