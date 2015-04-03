(ns sqls.plugin)


;; TODO: proto & reify are bleh, can't it be just map/record?


(defprotocol DatabaseDriverPlugin
  "Plugin that adds extra support for given database type."
  (classes
    [plugin]
    "Get classes that this driver exports.
    Returns a seq of pairs of driver name as string and description as string."))