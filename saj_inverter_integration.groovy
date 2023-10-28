/**
 *  Hubitat SAJ Solar Inverter integration
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2023-10-26
 *
 *  Requirements:
 *  SAJ Solar inverter with SAJ WiFi Module (R5/R6)
 */
 metadata {
	definition (name: "SAJ Solar Inverter", namespace: "SAJ", author: "magnus") {
		capability "Sensor"
		capability "EnergyMeter"
        capability "PowerMeter"
        capability "PowerSource"
        capability "Refresh"
        capability "TemperatureMeasurement"

		attribute "totalGen", "number"
        attribute "totalRunTime", "number"
		attribute "todayGen", "number"
		attribute "todayRunTime", "number"
		attribute "pv1vol", "number"
		attribute "pv1cur", "number"
		attribute "pv2vol", "number"
		attribute "pv2cur", "number"
		attribute "pv3vol", "number"
		attribute "pv3cur", "number"
		attribute "gridConPwr", "number"
		attribute "gridConFreq", "number"
		attribute "line1vol", "number"
		attribute "line2vol", "number"
		attribute "line3vol", "number"
		attribute "line1cur", "number"
		attribute "line2cur", "number"
		attribute "line3cur", "number"
		attribute "runState", "number"
		attribute "busVol", "number"
		attribute "devTemp", "number"
		attribute "co2Red", "number"
        attribute "tileHTML", "string"
	}

    preferences {
        input (
            name: "inverterIP",
            type: "string",
            title: "SAJ inverter IP-adress",
            description: "Enter the SAJ inverter local IP-adress",
            required: true,
            displayDuringSetup: true
        )
        
        input name: "debug", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def initialize() {
	if(debug) log.debug("init")
    clear()
    fetchInverterData()
    schedule("0 0/1 * * * ?", fetchInverterData)
}

def installed() {
	log.info "SAJ inverter installed"
    initialize()
}

def uninstalled() {
    log.info "SAJ inverter uninstalled."
    unschedule(fetchInverterData)
}

def updated() {
	log.info "SAJ inverter updated."
    state.version = version()
    unschedule(fetchInverterData)
    initialize()
}

def refresh()
{
    log.info "Refresh"
    fetchInverterData()
}

def fetchInverterData() {
	if(debug) log.debug("fetchInverterData")
    if(inverterIP == null){
        log.error("Inverter IP-adress is not set. Please set it in the settings.")
    } else {
        def inverterURL = "http://$inverterIP/status/status.php"
        if(debug) log.debug "URL: $inverterURL"

        def data = null
        try {
            data = inverterURL.toURL().text
        } catch (java.net.NoRouteToHostException noRoute) {
            if(debug) log.debug "No connection with: $inverterURL"
            data = null
        }

        try {
            if(data ==  null) {
                clear()
            }
            else {
                if(debug) log.debug "DATA: $data"

                def (
                    version,
                    totalGen,
                    totalRunTime,
                    todayGen,
                    todayRunTime,
                    pv1vol,
                    pv1cur,
                    pv2vol,
                    pv2cur,
                    pv3vol,
                    pv3cur,
                    pv1StrCurr1,
                    pv1StrCurr2,
                    pv1StrCurr3,
                    pv1StrCurr4,
                    pv2StrCurr1,
                    pv2StrCurr2,
                    pv2StrCurr3,
                    pv2StrCurr4,
                    pv3StrCurr1,
                    pv3StrCurr2,
                    pv3StrCurr3,
                    pv3StrCurr4,
                    gridConPwr,
                    gridConFreq,
                    line1vol,
                    line1cur,
                    line2vol,
                    line2cur,
                    line3vol,
                    line3cur,
                    busVol,
                    devTemp,
                    co2Red,
                    runState
                ) = data.split(",")

                setTotalGen(totalGen)
                setTotalRunTime(totalRunTime)
                setTodayGen(todayGen)
                setTodayRunTime(todayRunTime)
                setPv1Vol(pv1vol)
                setPv1Amp(pv1cur)
                setPv2Vol(pv2vol)
                setPv2Amp(pv2cur)
                setPv3Vol(pv3vol)
                setPv3Amp(pv3cur)
                setGridConPwr(gridConPwr)
                setGridConFreq(gridConFreq)
                //setLine1Vol(line1vol)
                //setLine1Amp(line1cur)
                //setLine2Vol(line2vol)
                //setLine2Amp(line2cur)
                //setLine3Vol(line3vol)
                //setLine3Amp(line3cur)
                setBusVol(busVol)
                setDevTemp(devTemp)
                setCo2red(co2Red)
                setRunState(runState)
            }

        } catch (e) {
            log.error "Unhandled error: $e"
            clear()
        }
        
        updateTile(state.gridConPwr, state.todayGen, state.totalGen)
        
    }
}

private clear() {
    if(debug) log.debug "Clearing live values"
    setPv1Vol(0)
    setPv1Amp(0)
    setPv2Vol(0)
    setPv2Amp(0)
    setPv3Vol(0)
    setPv3Amp(0)
    setGridConPwr(0)
    setGridConFreq(0)
    //setLine1Vol(0)
    //setLine1Amp(0)
    //setLine2Vol(0)
    //setLine2Amp(0)
    //setLine3Vol(0)
    //setLine3Amp(0)
    setBusVol(0)
    setDevTemp(0)
    setRunState(0)
}

private updateTile(power, todayGen, totalGen) {
    def tileHTML = "<table class=\"SolarInverter\">"
    tileHTML += "<caption><span class=\"material-symbols-outlined\">solar_power</span></caption>"
    tileHTML += "<tr><th>Power</th><td>${power} <span class=\"small\">W</span></td></tr>"
    tileHTML += "<tr><th>Today</th><td>${todayGen} <span class=\"small\">kWh</span></td></tr>"
    tileHTML += "<tr><th>Total</th><td>${totalGen} <span class=\"small\">kWh</span></td></tr>"
    tileHTML += "</table>"
    if (debug) log.debug "${tileHTML}"
    state.tileHTML = tileHTML
    sendEvent(name: "tileHTML", value: state.tileHTML)
} 

private setTotalGen(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "kWh"
    def descriptionText = "${device.displayName} total generated energy is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.totalGen = value
    sendEvent(name: "totalGen", value: state.totalGen, descriptionText: descriptionText, unit: unit)
}

private setTotalRunTime(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "h"
    def descriptionText = "${device.displayName} total runtime is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.totalRunTime = value
    sendEvent(name: "totalRuntime", value: state.totalRunTime, descriptionText: descriptionText, unit: unit)
}

private setTodayGen(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "kWh"
    def descriptionText = "${device.displayName} today generated energy is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.todayGen = value
    //sendEvent(name: "TodayGen", value: state.todayGen, descriptionText: descriptionText, unit: unit)
    sendEvent(name: "energy", value: state.todayGen, descriptionText: descriptionText, unit: unit)
}

private setTodayRunTime(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "h"
    def descriptionText = "${device.displayName} today runtime is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.todayRunTime = value
    sendEvent(name: "todayRuntime", value: state.todayRunTime, descriptionText: descriptionText, unit: unit)
}

private setPv1Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} PV1 voltage is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv1vol = value
    sendEvent(name: "pv1Vol", value: state.pv1vol, descriptionText: descriptionText, unit: unit)
}

private setPv1Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} PV1 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv1cur = value
    sendEvent(name: "pv1cur", value: state.pv1cur, descriptionText: descriptionText, unit: unit)
}

private setPv2Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} PV2 voltage is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv2vol = value
    sendEvent(name: "pv2Vol", value: state.pv2vol, descriptionText: descriptionText, unit: unit)
}

private setPv2Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} PV2 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv2cur = value
    sendEvent(name: "pv2cur", value: state.pv2cur, descriptionText: descriptionText, unit: unit)
}

private setPv3Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} PV3 voltage is ${vvalue} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv3vol = value
    sendEvent(name: "pv3vol", value: state.pv3vol, descriptionText: descriptionText, unit: unit)
}

private setPv3Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} PV3 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.pv3cur = value
    sendEvent(name: "pv3cur", value: state.pv3cur, descriptionText: descriptionText, unit: unit)
}

private setGridConPwr(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toFloat()
    def unit = "W"
    def descriptionText = "${device.displayName} grid connected power is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.gridConPwr = value
    //sendEvent(name: "GridConPwr", value: state.gridConPwr, descriptionText: descriptionText, unit: unit)
    sendEvent(name: "power", value: state.gridConPwr, descriptionText: descriptionText, unit: unit)
}

private setGridConFreq(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "Hz"
    def descriptionText = "${device.displayName} grid connected frequence is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.gridConFreq = value
    sendEvent(name: "gridConFreq", value: state.gridConFreq, descriptionText: descriptionText, unit: unit)
}

private setLine1Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} line 1 voltage is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line1vol = value
    sendEvent(name: "line1vol", state.line1vol, descriptionText: descriptionText, unit: unit)
}

private setLine1Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} line 1 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line1cur = value
    sendEvent(name: "Line1Amp", value: state.line1cur, descriptionText: descriptionText, unit: unit)
}

private setLine2Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} line 2 voltage is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line2vol = value
    sendEvent(name: "line2vol", value: state.line2vol, descriptionText: descriptionText, unit: unit)
}

private setLine2Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} line 2 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line2cur = value
    sendEvent(name: "line2cur", value: state.line2cur, descriptionText: descriptionText, unit: unit)
}

private setLine3Vol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} line 3 voltage is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line3vol = value
    sendEvent(name: "line3vol", value: state.line3vol, descriptionText: descriptionText, unit: unit)
}

private setLine3Amp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 100
    def unit = "A"
    def descriptionText = "${device.displayName} line 3 current is ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.line3cur = value
    sendEvent(name: "line3cur", value: state.line3cur, descriptionText: descriptionText, unit: unit)
}

private setRunState(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger()
    def descriptionText = "${device.displayName} running state ${value}"
    if (debug) log.debug "${descriptionText}"
    //state.runState = value
    //sendEvent(name: "runState", value: state.runState, descriptionText: descriptionText)
 
    //ENUM ["battery", "dc", "mains", "unknown"]
    if(value == 2) {
        state.runState = 'dc'
    }
    else {
        state.runState = 'mains'
    }
    sendEvent(name: "powerSource", value: state.runState, descriptionText: descriptionText)
}

private setBusVol(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "V"
    def descriptionText = "${device.displayName} bus voltage ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.busVol = value
    sendEvent(name: "busVol", value: state.busVol, descriptionText: descriptionText, unit: unit)
}

private setDevTemp(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "°C"
    def descriptionText = "${device.displayName} device temperature ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.devTemp = value
    sendEvent(name: "temperature", value: state.devTemp, descriptionText: descriptionText, unit: unit)
}

private setCo2red(rawValue) {
    if(rawValue.toInteger() == 65535) rawValue = 0;
    def value = rawValue.toInteger() / 10
    def unit = "t"
    def descriptionText = "${device.displayName} CO2 emission reduction ${value} ${unit}"
    if (debug) log.debug "${descriptionText}"
    state.co2Red = value
    sendEvent(name: "co2Red", value: state.co2Red, descriptionText: descriptionText, unit: unit)
}
