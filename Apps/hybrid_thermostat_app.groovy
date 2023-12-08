definition(
    name: "Hybrid Thermostat App",
    namespace: "Temperature",
    author: "Magnus Forslund",
    description: "",
    category: "Temperature",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Thermostat") {
        input( type: "string", name: "thermostatName", title: "Thermostat name", required: true)
        input "tempSensor", "capability.temperatureMeasurement", title: "Select temperature sensor", submitOnChange: true, required: true, multiple: false
        input( type: "int", name: "thermostatSetpoint", title: "Thermostat setpoint", required: true)
        input "heatingSwitch", "capability.switch", title: "Heating switch", submitOnChange: true, required: true, multiple: false
    }
    section("Logging") {
        input name: "logEnable", type: "bool", title: "Enable logging?"
    }
}

def mainPage() {
}

def installed() {
    if(logEnable) log.debug "installed"
    updated()
}

def updated() {
    if(logEnable) log.debug "updated"
    unsubscribe()
    initialize()
}

def initialize() {
    if(logEnable) log.debug("initialize")
	subscribe(tempSensor, "temperature", tempChangeHandler)
    getDeviceDriver().setThermostatSetpoint(thermostatSetpoint.toFloat())
    tempChangeHandler()
}

def uninstalled() {
    unschedule(calculateSmartGridState)
}

def tempChangeHandler(evt) {
    if(logEnable)  log.debug("Temp changed")
    
    def sensorTemp = tempSensor.currentTemperature
	def displayName = tempSensor.displayName

    if(logEnable) log.info("Current temperature is ${sensorTemp}° on ${displayName}")
    getDeviceDriver().setCurrentTemp(sensorTemp)

    if(logEnable)  log.debug("Heating: ${sensorTemp} < ${thermostatSetpoint.toFloat()}")
    def heatingOn = sensorTemp < thermostatSetpoint.toFloat()
    getDeviceDriver().setHeatingOn(heatingOn)

    if(heatingOn) {
        heatingSwitch.on()
    }
    else {
        heatingSwitch.off()
    }

}

def getDeviceDriver() {
    def deviceDriverID = "HybridThermostat_${app.id}"
    def deviceDriver = getChildDevice(deviceDriverID)
    if(!deviceDriver) {
        log.info("New device driver created: ${deviceDriverID}")
        deviceDriver = addChildDevice("sensors", "Basic Thermostat", deviceDriverID, null, [label: thermostatName, name: thermostatName])
    }
    return deviceDriver
}
