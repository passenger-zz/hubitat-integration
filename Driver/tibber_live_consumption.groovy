/**
 *  Tibber Live Consumption
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2024-09-03
 *
 */

import groovy.json.JsonSlurper

metadata {
	definition (name: "Tibber Live Consumption", namespace: "Tibber", author: "Ivar Holand") {
		capability "Sensor"
		capability "EnergyMeter"
		capability "PowerMeter"
		capability "Switch"

        attribute "power_w_unit", "String"
        attribute "tileHTML", "String"


		command "connectSocket"
		command "closeSocket"
		command "querySubscriptionURL"
	}

	preferences {
		input name: "tibber_apikey", type: "password", title: "API Key", description: "Enter the Tibber API key.<p><i>This can be found on https://developer.tibber.com/explorer. Sign in and click Load Personal Token.</i></p>", required: true, displayDuringSetup: true
		input name: "tibber_homeId", type: "text", title: "homeId", description: "Enter the Tibber homeId: <p><i>This can be found on https://developer.tibber.com/explorer. Open the Real time subscription example, homeId should be on the left.</i></p>", required: true, displayDuringSetup: true
		input name: "threshold", type: "number", title: "Limit hour average to:", default: 5000
		input name: "run_config", type: "enum", title: "Configure run schedule:", options: [1:"Continuous without auto reconnect ~7000 evt/hour", 2:"Continuous with auto reconnect ~7000 evt/hour", 3: "Every 3 min ~80 evt/hour", 5: "Every 5 min  ~48 evt/hour", 10: "Every 10 min ~24 evt/hour", 15: "Every 15 min ~16 evt/hour", 20: "Every 20 min ~12 evt/hour", 30: "Every 30 min ~8 evt/hour"]

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def parse(description) {
	if(logEnable) log.debug "parse $description"

	def jsonSlurper = new JsonSlurper()
	def object = jsonSlurper.parseText(description)

	if (object.type == "connection_ack") {
		if(logEnable) log.debug "Connection accepted"

		state.error_count = 0;

		sub = '{"type": "subscribe", "id": "'+ device.getDeviceNetworkId() +'", "payload": {"query": "subscription{liveMeasurement(homeId:\\"'+ tibber_homeId +'\\"){timestamp\\r\\npower\\r\\npowerProduction\\r\\naccumulatedConsumption\\r\\naccumulatedProduction\\r\\naccumulatedCost\\r\\naccumulatedReward\\r\\n}}"}}'

    
		interfaces.webSocket.sendMessage(sub)
	}

	if (object.type == "error") {
		log.error "Connection error with Tibber"

		querySubscriptionURL()

		if (state.error_count == null) {
			state.error_count = 1;
		} else {
			state.error_count = state.error_count + 1
		}

		// Safest thing to do is to unschedule
		if (state.error_count > 10) {
			unschedule()
		}
	}

	if (object.type == "next") {

		def timestamp_str = object.payload.data.liveMeasurement.timestamp
		def power = ((object.payload.data.liveMeasurement.power - object.payload.data.liveMeasurement.powerProduction).toDouble() / 1000).round(2)
        def accumulatedConsumption = object.payload.data.liveMeasurement.accumulatedConsumption.toDouble().round(0).toInteger()
        def accumulatedProduction = object.payload.data.liveMeasurement.accumulatedProduction.toDouble().round(0).toInteger()
        def accumulatedCost = object.payload.data.liveMeasurement.accumulatedCost.toDouble().round(2)
        def accumulatedReward = object.payload.data.liveMeasurement.accumulatedReward.toDouble().round(2)
        def accumulatedTotal = (accumulatedCost - accumulatedReward).round(2)
        
		sendEvent(name:"power", value:power, unit:"kW")

        updateTile(power, accumulatedConsumption, accumulatedProduction, accumulatedTotal, "kW")
        
		if (run_config.toInteger() > 2) {
			closeSocket()
		}
	}
}

private updateTile(power, accumulatedConsumption, accumulatedProduction, costTotal, unit) {
    def tileHTML = "<table class=\"tibber custom\">"
    tileHTML += "<caption><span class=\"material-symbols-outlined\">house</span></caption>"
    tileHTML += "<tr class=\"head\"><th colspan=\"2\">${power} <span class=\"small\">${unit}</span></th></tr>"
    tileHTML += "<tr><th>In</th><td>${accumulatedConsumption} <span class=\"small\">kw/h</span></td></tr>"
    tileHTML += "<tr><th>Out</th><td>${accumulatedProduction} <span class=\"small\">kw/h</span></td></tr>"
    tileHTML += "<tr><th>Cost</th><td>${costTotal} <span class=\"small\">kr</span></td></tr>"
    tileHTML += "</table>"
    if (debug) log.debug "${tileHTML}"
    state.tileHTML = tileHTML
    sendEvent(name: "tileHTML", value: state.tileHTML)
} 

def webSocketStatus(String message) {
	if(logEnable) log.debug "webSocketStatus: $message"

	if (message == "status: open") {
		sendEvent(name:"switch", value:"on")
	}

	if (message == "status: closing") {
		sendEvent(name:"switch", value:"off")
	}

	if (message == "failure: null")  {
		sendEvent(name:"switch", value:"off")

		if (run_config == 2) {
			runIn(30, connectSocket)
		}
	}
}

def graphQLApiQuery(){
	return '{"query":"{ viewer { websocketSubscriptionUrl } }"}'
}

def querySubscriptionURL() {
	def params = [
		uri: "https://api.tibber.com/v1-beta/gql",
		headers: ["Content-Type": "application/json" , "Authorization": "Bearer $tibber_apikey"],
		body: graphQLApiQuery()
		]

	try {
		httpPostJson(params) { resp ->
			if (resp.status == 200) {
				if(logEnable) log.debug resp.data
				state.subscriptionURL = resp.data.data.viewer.websocketSubscriptionUrl
			}
		}
	} catch (e) {
		log.error "something went wrong: $e"
	}
}

def connectSocket() {
	auth = '{"type":"connection_init","payload":{"token":"'+ tibber_apikey +'"}}'

	if (state.subscriptionURL == null) {
		querySubscriptionURL()
	}

	try {
		interfaces.webSocket.connect(state.subscriptionURL, pingInterval:20, headers: ["Sec-WebSocket-Protocol": "graphql-transport-ws", "User-Agent":"Hubitat/${location.hub.firmwareVersionString} iholand-TibberLiveConsumption/2.0"])
		interfaces.webSocket.sendMessage(auth)
	} 
	catch (Exception e) {
		log.error "Error during WebSocket connection: ${e.message}. Retrying in 30 seconds."
		runIn(30, connectSocket)  // Retry after 30 seconds
    }

}

def endConnection() {
	completeMessage = '{"type":"complete", "id":"'+ device.getDeviceNetworkId() +'"}'

	if(logEnable) log.debug completeMessage

    try {
		interfaces.webSocket.sendMessage(completeMessage)
	} 
	catch (Exception e) {
        log.error "Error during WebSocket disconnection: ${e.message}"
    }
}

def updated(settings) {
	log.debug "updated with settings: ${settings}"

	closeSocket()

	unschedule()

	querySubscriptionURL()

	if (run_config.toInteger() > 2) {
		//Schedule run

		schedule("0 */${run_config} * ? * *", connectSocket)
		connectSocket()

	} else {
		connectSocket()
	}
}

def closeSocket() {
	if(logEnable) log.debug "closeSocket"
	endConnection()

	interfaces.webSocket.close()
}

def on() {
	closeSocket()
	connectSocket()
}

def off() {
	closeSocket()
}
