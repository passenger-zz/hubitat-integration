/**
 *  Basic Thermostat
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2023-12-06
 */
 metadata {
	definition (name: "Basic Thermostat", namespace: "sensors", author: "magnus") {
		capability "Sensor"
        capability "TemperatureMeasurement"

		attribute "currentTemp", "number"
		attribute "thermostatSetpoint", "number"
        attribute "heatingOn", "bool"

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
    
    state.thermostatSetpoint = 20
    state.heatingOn = false
            
    sendEvent(name: "temperature", value: currentTemp, descriptionText: "Current temperature changed")
    sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, descriptionText: "Thermostat setpoint changed")
    sendEvent(name: "heatingOn", value: heatingOn, descriptionText: "Thermostat mode changed")

    updateTile()
}

def installed() {
	log.info "installed"
    initialize()
}

def uninstalled() {
    log.info "uninstalled"
    unschedule()
}

def updated() {
	log.info "updated"
    state.version = version()
    unschedule()
    initialize()
}

public setThermostatSetpoint(t) {
    if(debug) log.debug("setThermostatSetpoint: old=${state.thermostatSetpoint} new=${t}")
    if(t != thermostatSetpoint) {
        state.thermostatSetpoint = t
        sendEvent(name: "thermostatSetpoint", value: state.thermostatSetpoint, descriptionText: "Thermostat setpoint changed")
        updateTile()
    }
}

public setCurrentTemp(t) {
    if(debug) log.debug("currentTemp: old=${state.currentTemp} new=${t}")
    if(t != currentTemp) {
        state.currentTemp = t
        sendEvent(name: "temperature", value: state.currentTemp, descriptionText: "Current temperature changed")
        updateTile()
    }
}

public setHeatingOn(newState) {
    if(debug) log.debug("setHeatingOn: old=${state.heatingOn} new=${newState}")
    if(newState != heatingOn) {
        state.heatingOn = newState
        sendEvent(name: "heatingOn", value: state.heatingOn, descriptionText: "Heating on state")
        updateTile()
    }
}

private updateTile() {

    if (debug) log.debug("updateTile");

    def tileHTML = "<table class=\"BasTherm custom ${state.heatingOn ? "heat_on" : "heat_off"}\">"
    tileHTML += "<caption><span class=\"material-symbols-outlined\">device_thermostat</span></caption>"
    tileHTML += "<tr class=\"head\"><th colspan=\"2\">${state.currentTemp} <span class=\"small\">&degC</span></th></tr>"
    tileHTML += "<tr><th>Setpoint</th><td>${state.thermostatSetpoint} <span class=\"small\">&deg;C</span></td></tr>"
    tileHTML += "</table>"
    if (debug) log.debug "${tileHTML}"    
    state.tileHTML = tileHTML
    sendEvent(name: "tileHTML", value: state.tileHTML)
} 
