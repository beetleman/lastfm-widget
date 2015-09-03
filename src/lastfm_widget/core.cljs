(ns ^:figwheel-always lastfm-widget.core
    (:require-macros
     [cljs.core.async.macros :as m :refer [go]])
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs.core.async :refer [chan close!]]
              [ajax.core :refer [GET POST]]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"
                          :tracks []
                          :error-api nil
                          :error nil}))


(def ^:private api-url "http://ws.audioscrobbler.com/2.0/")
(def ^:private api-key "7125d4e86e20b283e109669693c4465d")
(def ^:private api-format "json")


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


(defn get-plaing-now [tracks]
  (first (filter :nowplaying tracks)))


(defn track-view [track owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (dom/li nil
              (:name track)))))


(defn lastfm-widget-view [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (.log js/console _)
      (go
        (loop []
          (getRecentTracks "robal_pro"
                           (make-handler :tracks app-state [:recenttracks :track])
                           (make-handler :error-api app-state) 10)
          (<! (timeout 5000))
          (recur))))
    om/IRenderState
    (render-state [this state]
      (apply dom/div nil
             (om/build-all track-view (:tracks data))))))


(om/root
  lastfm-widget-view
  app-state
  {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
