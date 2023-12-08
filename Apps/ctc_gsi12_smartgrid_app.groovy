definition(
    name: "CTC SGi12 SmartGrid App",
    namespace: "CTC",
    author: "Magnus Forslund",
    description: "SmartGrid control",
    category: "CTC",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Settings", install: true, uninstall: true) {        
        section("Tibber") {
            input "tibber", "capability.energyMeter", title: "Tibber", submitOnChange: true, required: true, multiple: false
        }
        section("Price span (öre)") {
            input( type: "int", name: "HighPrice", title: "High price", required: true)
            input( type: "int", name: "LowPrice", title: "Low price", required: true)
        }
        section("SmartGrid switches") {
            input "smartA", "capability.switch", title: "Select SmartA Switch", submitOnChange: true, required: true, multiple: false
            input "smartB", "capability.switch", title: "Select SmartB Switch", submitOnChange: true, required: true, multiple: false
        }
        section("Logging") {
            input name: "logEnable", type: "bool", title: "Enable logging?"
        }
    }
}

def installed() {
    if(logEnable) log.debug "installed"
    updated()
}

def updated() {
    if(logEnable) log.debug "updated"
    unschedule(calculateSmartGridState)
    initialize()
}

def initialize() {
    state.currentSmartGridState = null
    if(logEnable) log.debug("initialize")
    getDeviceDriver()
    schedule("0 * * ? * * *", calculateSmartGridState) // Every minute
}

def uninstalled() {
    unschedule(calculateSmartGridState)
}

def calculateSmartGridState(evt) {
    if(logEnable) log.debug("Calculate SmartGrid mode")

    def tibberPriceNow = tibber?.currentValue("price").toFloat()
    def tibberPriceNextHour = tibber?.currentValue("priceNextHour").toFloat()
    def tibberPricePlus2Hour = tibber?.currentValue("pricePlus2Hour").toFloat()

    if(logEnable) log.debug("Tibber price: ${tibberPriceNow}; +1h: ${tibberPriceNextHour}; +2h: ${tibberPricePlus2Hour}")

    def tibberPriceMaxDay = tibber?.currentValue("priceMaxDay").toFloat()
    def tibberPriceAvrDay = tibber?.currentValue("priceMedDay").toFloat()
    def tibberPriceMinDay = tibber?.currentValue("priceMinDay").toFloat()

    if(logEnable) log.debug("Tibber price Min: ${tibberPriceMinDay}; Avr: ${tibberPriceAvrDay}; Max: ${tibberPriceMaxDay}")

    def prefHighPrice = Math.round(HighPrice.toFloat())
    def prefLowPrice = Math.round(LowPrice.toFloat())

    if(logEnable) log.debug("Price span: Low=${prefLowPrice} high=${prefHighPrice}")

    def dynamicThreshold = prefHighPrice
    if(tibberPriceNow < prefLowPrice) {
        if(logEnable) log.debug("Price < prefs low price")
        dynamicThreshold = Math.round(prefHighPrice)
    }
    else if(tibberPriceNow <= prefHighPrice) {
        dynamicThreshold = Math.round((tibberPriceMaxDay - tibberPriceAvrDay) / 1.5 + tibberPriceAvrDay)
        if(logEnable) log.debug("Price < prefs high price: (${tibberPriceMaxDay} - ${tibberPriceAvrDay})/1.5 + ${tibberPriceAvrDay} = ${dynamicThreshold}")
        if(dynamicThreshold > prefHighPrice) {
            if(logEnable) log.debug("dynamicThreshold > prefs HighPrice")
            dynamicThreshold = tibberPriceNow
        }
    }
    else {
        if(logEnable) log.debug("Price > prefs high price")
        dynamicThreshold = Math.round(prefHighPrice)
    }

    if(logEnable) log.debug("dynamicThreshold: ${dynamicThreshold}")

    def newState

    if(tibberPriceNow <= dynamicThreshold && tibberPriceNextHour <= dynamicThreshold && tibberPricePlus2Hour > dynamicThreshold) {
        newState = "OVER_CAPACITY"
    }
    else if(tibberPriceNow <= dynamicThreshold) {
        newState = "LOW_PRICE"
    }
    else {
        newState = "HIGH_PRICE"
    }
    
    if (state.currentSmartGridState != newState) {
        log.info("${newState} MODE")
        state.currentSmartGridState = newState
        setActiveState(newState)
    }
    
    getDeviceDriver().setSmartGridMode(state.currentSmartGridState)
    getDeviceDriver().setPriceThreshold(dynamicThreshold)
    getDeviceDriver().setDynamicPriceSpan(prefLowPrice, prefHighPrice)
}

def setActiveState(String state) {
    // Perform actions based on the state (unchanged states are not logged here)
    switch (state) {
        case "OVER_CAPACITY":
            smartA.on()
            smartB.on()
            break
        case "LOW_PRICE":
            smartA.off()
            smartB.on()
            break
        case "HIGH_PRICE":
            smartA.off()
            smartB.off()
            break
        case "BLOCKED":
            smartA.on()
            smartB.off()
            break
        default:
            log.warn("Unknown SmartGridState: $state")
    }
}

def getDeviceDriver() {
    def deviceDriverID = "CTC_GSi12_${app.id}"
    def ctcGSi12Dev = getChildDevice(deviceDriverID)
    if(!ctcGSi12Dev) {
        log.info("New device driver created: ${deviceDriverID}")
        ctcGSi12Dev = addChildDevice("CTC", "CTC GSi12", deviceDriverID, null, [label: thisName, name: thisName])
    }
    return ctcGSi12Dev
}
