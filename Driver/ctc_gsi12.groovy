/**
 *  CTC GSi12 Driver
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2023-12-01
 */
 metadata {
	definition (name: "CTC GSi12", namespace: "CTC", author: "magnus") {
		capability "Sensor"

		attribute "smartGridMode", "string"
        attribute "priceThreshold", "number"
        attribute "priceLow", "number"
        attribute "priceHi", "number"
        
        attribute "tileHTML", "string"
	}

    preferences {        
        input name: "debug", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def version() {
    return "0.1"
}

def initialize() {
	if(debug) log.debug("init")
    updateState()
    state.smartGridMode = "Unknown"
    state.priceThreshold = 200
    state.priceHi = 400
    state.priceLow = 100
    //schedule("0 * * ? * * *", updateState) // Every minute
}

def updateState() {
    if(debug) log.debug("UpdateState")
    smartGridMode = state.smartGridMode
    priceThreshold = state.priceThreshold
    priceLow = state.priceLow
    priceHi = state.priceHi
    updateTile(smartGridMode, priceThreshold, priceLow, priceHi)
}

def installed() {
	log.info "CTC GSi12 installed"
    initialize()
}

def uninstalled() {
    log.info "CTC GSi12 uninstalled."
    unschedule(updateState)
}

def updated() {
	log.info "CTC GSi12 updated."
    state.version = version()
    unschedule(updateState)
    initialize()
}

def refresh()
{
    log.info "Refresh"
    fetchInverterData()
}

public setSmartGridMode(mode) {
    def currentMode = state.smartGridMode
    switch (mode) {
        case "OVER_CAPACITY":
            state.smartGridMode = "Överkapacitet"
            break
        case "LOW_PRICE":
            state.smartGridMode = "Lågpris"
            break
        case "HIGH_PRICE":
            state.smartGridMode = "Högpris"
            break
        case "BLOCKED":
            state.smartGridMode = "Blockerad"
            break
        default:
            log.warn("Unknown SmartGridState: $state")
            state.smartGridMode = state
    }
    if(currentMode != state.smartGridMode) {
        def current = state.smartGridMode
        sendEvent(name: "smartGridMode", value: state.smartGridMode, descriptionText: "SmartGrid mode changed")
        updateTile(state.smartGridMode, state.priceThreshold, state.priceHi, state.pricelow)
    }
}

public setDynamicPriceSpan(priceLow, priceHi) {
    if (debug) log.debug("Set dynamic price range: Low=${priceLow} Hi=${priceHi}");
    state.priceLow = priceLow
    state.priceHi = priceHi
    updateTile(state.smartGridMode, state.priceThreshold, state.priceHi, state.priceLow)
}

public setPriceThreshold(price) {
    if (debug) log.debug("Set threshold: ${price}");
    def currentPrice = state.priceThreshold
    if(price != currentPrice) {
        state.priceThreshold = price
        sendEvent(name: "priceThreshold", value: state.priceThreshold, descriptionText: "PriceThreshold changed")        
        updateTile(state.smartGridMode, state.priceThreshold, state.priceHi, state.pricelow)
    }
}

private updateTile(smartGridMode, priceThreshold, priceHi, priceLow) {

    if (debug) log.debug("updateTile");

    def tileHTML = "<table class=\"CTCGSi12 custom\">"
    tileHTML += "<caption><span class=\"material-symbols-outlined\">water_heater</span></caption>"
    tileHTML += "<tr class=\"head\"><th colspan=\"2\">${smartGridMode}</th></tr>"
    tileHTML += "<tr><th>Threshold</th><td>${priceThreshold} <span class=\"small\">öre</span></td></tr>"
    tileHTML += "<tr><th>Dynamic</th><td>${priceLow} to ${priceHi} <span class=\"small\">öre</span></td></tr>"
    tileHTML += "</table>"
    if (debug) log.debug "${tileHTML}"
    state.tileHTML = tileHTML
    sendEvent(name: "tileHTML", value: state.tileHTML)
} 
