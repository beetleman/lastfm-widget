(ns ^:figwheel-always lastfm-widget.core
    (:require-macros
     [cljs.core.async.macros :as m :refer [go]])
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs.core.async :refer [chan close!]]
              [ajax.core :refer [GET POST]]))

(enable-console-print!)


(def ^:private api-url "http://ws.audioscrobbler.com/2.0/")
(def ^:private api-key "7125d4e86e20b283e109669693c4465d")
(def ^:private api-format "json")
(def ^:private default-album-cover-url "img/cover.png")


(defn GET-params
  ([method]
   (GET-params method {}))
  ([method params]
   (merge {:api_key api-key :method method :format api-format} params)))


(defn make-handler
  ([dest-key dest] (make-handler dest-key dest []))
  ([dest-key dest get-in-path]
   (fn [response]
     (let [response-error (if (:error response) response nil)
           response (get-in response get-in-path)]
       (swap! dest assoc :api_error response-error)
       (if response
         (swap! dest assoc dest-key response))))))


(defn getRecentTracks
  ([user handler error-handler]
   (getRecentTracks user handler error-handler 50))
  ([user handler error-handler limit]
   (GET api-url
        :params (GET-params "user.getRecentTracks"
                            {:user user :limit limit})
        :response-format (keyword api-format)
        :keywords? (= api-format "json")
        :handler handler
        :error-handler error-handler)))


(defn timeout [ms]
  (let [c (chan)]
    (js/setTimeout (fn [] (close! c)) ms)
    c))


(defn plaing-now? [track]
  (get-in track [(keyword "@attr") :nowplaying]))


(defn get-title [{:keys [name url]}]
  (dom/a #js {:className "title"
              :href url}
            name))


(defn get-album [{:keys [album]}]
  (dom/div #js {:className "album"}
            (:#text album)))


(defn get-artist [{:keys [artist]}]
  (dom/div #js {:className "artist"}
           (:#text artist)))


(defn get-album-cover-url [{:keys [image]} size]
  (let [url (some (fn [x]
                    (if (= (:size x) size) (:#text x)))
                  image)]
    (if (-> url count zero?)
      default-album-cover-url
      url)))


(defn get-album-cover [{:keys [album] :as track}]
  (dom/img #js {:className "cover"
                :src (get-album-cover-url track "medium")}))


(defn track-view [track owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (apply dom/li #js {:className (if (plaing-now? track)
                                      "track plaing-now"
                                      "track")}
             (map #(% track)
                  [get-album-cover get-title get-artist get-album])))))


(defn create-lastfm-widget-view [user_name track_number app-state]
  (fn [data owner]
    (reify
      om/IWillMount
      (will-mount [_]
        (go
          (loop []
            (getRecentTracks user_name
                             (make-handler :tracks app-state [:recenttracks :track])
                             (make-handler :error-api app-state)
                             track_number)
            (<! (timeout 5000))
            (recur))))
      om/IRenderState
      (render-state [this state]
        (apply dom/ul #js {:className "tracks"}
               (om/build-all track-view (:tracks data))))))
)


(defn create [user_name track_number id]
  (let [app-state (atom {:tracks []
                 :error-api nil
                 :error nil})]
    (om/root
     (create-lastfm-widget-view user_name track_number app-state)
     app-state
     {:target (. js/document (getElementById id))})))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
