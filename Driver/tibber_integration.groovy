/**
 *  Hubitat Tibber integration
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2023-10-28
 *
 *  Requirements:
 *  Tibber API key and password
 */
metadata {
	definition (name: "Tibber price device handler", namespace: "Tibber", author: "Magnus Forslund") {
		capability "Sensor"
		capability "Energy Meter"
        capability "Refresh"
		attribute "price", "number"
		attribute "priceNextHour", "number"
		attribute "priceNextHourLabel", "string"
		attribute "pricePlus2Hour", "number"
		attribute "pricePlus2HourLabel", "string"
		attribute "priceMaxDay", "number"
		attribute "priceMaxDayLabel", "string"
		attribute "priceMinDay", "number"
		attribute "priceMinDayLabel", "string"
		attribute "currency", "string"
		attribute "priceMedDay", "number"
        attribute "tileHTML", "string"
	}

    preferences {
        input (
            type: "paragraph",
            element: "paragraph",
            title: "Tibber API key",
            description: "You'll find the API key at https://developer.tibber.com/settings/accesstoken"
        )
        input (
            name: "tibber_apikey",
            type: "password",
            title: "API Key",
            description: "Enter the Tibber API key",
            required: true,
            displayDuringSetup: true
        )
        input name: "debug", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def initialize() {
	state.price = 100;
	if(debug) log.debug("init")
    getPrice()
    schedule("0 2 * * * ?", getPrice)
}

def installed() {
	if(debug) log.debug "Installed"
    initialize()
}

def updated() {
	if(debug) log.debug "Updated"
    initialize()
}

def refresh() {
    if(debug) log.debug "refresh"
    unschedule(getPrice)
    getPrice()
    schedule("0 2 * * * ?", getPrice)    
}

def getPrice() {
	if(debug) log.debug("getPrice")
    if(tibber_apikey == null){
        log.error("API key is not set. Please set it in the settings.")
    } else {
        def params = [
            uri: "https://api.tibber.com/v1-beta/gql",
            headers: ["Content-Type": "application/json;charset=UTF-8" , "Authorization": "Bearer $tibber_apikey"],
            body: graphQLApiQuery()
        ]
        try {
            httpPostJson(params) { resp ->
                if(resp.status == 200){
                    if(debug) log.debug "${resp.data}"
                    
                    if(resp.data.data.viewer.homes[0].currentSubscription == null) {
                        throw new Exception("Missing subscription!")
                    }
                    
                    def today = resp.data.data.viewer.homes[0].currentSubscription.priceInfo.today
                    def tomorrow = resp.data.data.viewer.homes[0].currentSubscription.priceInfo.tomorrow
                    
                    def consumption = null
                    def consumptionUnit = null
                    if(resp.data.data.viewer.homes[0].consumption.length > 0) {
                        log.debug "consumption: ${resp.data.data.viewer.homes[0].consumption}"

                        consumption = resp.data.data.viewer.homes[0].consumption.nodes[0].consumption?.toFloat()
                        consumptionUnit = resp.data.data.viewer.homes[0].consumption.nodes[0].consumptionUnit?.toString()
                    }
                    else {
                        log.debug "Missing consumption!"
                        consumption = {}
                        consumption.price = 0
                        consumption.unit = 'N/A'
                    }

                    def priceInfo = resp.data.data.viewer.homes[0].currentSubscription.priceInfo
                    log.debug "Current priceInfo: ${priceInfo.current}"
                    
                    def price = Math.round(priceInfo.current.total * 100)
                    def priceMaxDay = Math.round(MaxValue(today) *100)
                    def priceMaxDayLabel = "Max price @ ${MaxValueTimestamp(today)}"
                    def priceMinDay = Math.round(MinValue(today) *100)
                    def priceMinDayLabel = "Min price @ ${MinValueTimestamp(today)}"
                    def priceMedDay = Math.round(MedValue(today) *100)

                    def priceList = today
                    tomorrow.each{
                        priceList << it
                    }
                    def priceNextHours = PriceNextHours(priceList)
                    def priceNextHour = Math.round(priceNextHours[0] *100)
                    def priceNextHourLabel = "@ ${priceNextHours[2]}"
                    def pricePlus2Hour = Math.round(priceNextHours[1] *100)
                    def pricePlus2HourLabel = "@ ${priceNextHours[3]}"
                    def currency = priceInfo.current.currency

                    def priceUnit = "${currencyToMinor(currency)}/kWh"

                    state.currency = currency
                    state.price = price
                    state.priceNextHour = priceNextHour
                    state.priceNextHourLabel = priceNextHourLabel
                    state.pricePlus2Hour = pricePlus2Hour
                    state.pricePlus2HourLabel = pricePlus2HourLabel
                    state.priceMaxDay = priceMaxDay
                    state.priceMaxDayLabel = priceMaxDayLabel
                    state.priceMinDay = priceMinDay
                    state.priceMinDayLabel = priceMinDayLabel
                    state.priceMedDay = priceMedDay

                    sendEvent(name: "energy", value: consumption, unit: consumptionUnit)
                    sendEvent(name: "price", value: state.price, unit: priceUnit)
                    sendEvent(name: "priceNextHour", value: state.priceNextHour, unit: priceUnit)
                    sendEvent(name: "pricePlus2Hour", value: state.pricePlus2Hour, unit: priceUnit)
                    sendEvent(name: "priceMaxDay", value: state.priceMaxDay, unit: priceUnit)
                    sendEvent(name: "priceMinDay", value: state.priceMinDay, unit: priceUnit)
                    sendEvent(name: "priceMedDay", value: state.priceMedDay, unit: priceUnit)

                    sendEvent(name: "priceNextHourLabel", value: state.priceNextHourLabel)
                    sendEvent(name: "pricePlus2HourLabel", value: state.pricePlus2HourLabel)
                    sendEvent(name: "priceMaxDayLabel", value: state.priceMaxDayLabel)
                    sendEvent(name: "priceMinDayLabel", value: state.priceMinDayLabel)

                    sendEvent(name: "currency", value: state.currency)
                    
                    updateTile(price, priceNextHour, pricePlus2Hour, priceMaxDay, priceMinDay, priceMedDay, priceUnit)
                }
            }
        } catch (e) {
            log.error "something went wrong: $e"
        }
    }
}

private updateTile(price, priceNextHour, pricePlus2Hour, priceMaxDay, priceMinDay, priceMedDay, unit) {
    def tileHTML = "<table class=\"tibber custom\">"
    tileHTML += "<caption><span class=\"material-symbols-outlined\">electric_bolt</span></caption>"
    tileHTML += "<tr class=\"head\"><th colspan=\"2\">${price} <span class=\"small\">${unit}</span></th></tr>"
    tileHTML += "<tr><th>+1h</th><td>${priceNextHour} <span class=\"small\">${unit}</span></td></tr>"
    tileHTML += "<tr><th>+2h</th><td>${pricePlus2Hour} <span class=\"small\">${unit}</span></td></tr>"
    tileHTML += "<tr><th>Range</th><td>${priceMinDay} to ${priceMaxDay} <span class=\"small\">${unit}</span></td></tr>"
    tileHTML += "</table>"
    if (debug) log.debug "${tileHTML}"
    state.tileHTML = tileHTML
    sendEvent(name: "tileHTML", value: state.tileHTML)
} 

def parse(String description) {
    if(debug) log.debug "parse description: ${description}"
    def eventMap = [
        createEvent(name: "energy", value: state.price, unit: state.currency)
        ,createEvent(name: "price", value: state.price, unit: state.currency)
        ,createEvent(name: "priceNextHour", value: state.priceNextHour, unit: state.currency)
        ,createEvent(name: "pricePlus2Hour", value: state.pricePlus2Hour, unit: state.currency)
        ,createEvent(name: "priceMaxDay", value: state.priceMaxDay, unit: state.currency)
        ,createEvent(name: "priceMinDay", value: state.priceMinDay, unit: state.currency)
        ,createEvent(name: "priceMedDay", value: state.priceMedDay, unit: state.currency)
        ,createEvent(name: "priceNextHourLabel", value: state.priceNextHourLabel)
        ,createEvent(name: "pricePlus2HourLabel", value: state.pricePlus2HourLabel)
        ,createEvent(name: "priceMaxDayLabel", value: state.priceMaxDayLabel)
        ,createEvent(name: "priceMinDayLabel", value: state.priceMinDayLabel)    
        ,createEvent(name: "currencyLabel", value: state.currency, unit: state.currency)   
    ]
    if(debug) log.debug "Parse returned ${description}"
    return eventMap
}

def currencyToMinor(String currency){
	def currencyUnit = "";
	switch(currency){
    	case "NOK":currencyUnit = "Øre";break;
        case "SEK":currencyUnit = "Öre";break;
        case "USD":currencyUnit = "Penny";break;
        default: currencyUnit = "";break;
    }
    return currencyUnit;
    
}
def backgroundColors(){
    return [
		[value: 20, color: "#02A701"],
      	[value: 39, color: "#6CCD00"],
      	[value: 59, color: "#ECD400"],
      	[value: 74, color: "#FD6700"],
      	[value: 95, color: "#FE3500"]
    ]
}

def graphQLApiQuery(){
    return '{"query": "{viewer {homes {currentSubscription {priceInfo { current {total currency} today{ total startsAt } tomorrow{ total startsAt }}} consumption(resolution: HOURLY, last: 1) { nodes { from to cost unitPrice unitPriceVAT consumption consumptionUnit }}}}}", "variables": null, "operationName": null}';
}

def MaxValueTimestamp(List values){
	def max = 0
    def maxTimestamp = ""
	values.each{
    	def timestamp = it.startsAt
        def total = it.total
        if(total>max){
        	max = it.total
            maxTimestamp = timestamp
        }
    }
    return maxTimestamp.substring(11,13)
}
def MaxValue(List values){
	def max = 0
    def maxTimestamp = ""
	values.each{
    	def timestamp = it.startsAt
        def total = it.total
        if(total>max){
        	max = it.total
            maxTimestamp = timestamp
        }
    }
    return max
}

def MinValueTimestamp(List values){
	def min = 1000
    def minTimestamp = ""
	values.each{
    	def timestamp = it.startsAt
        def total = it.total   
        if(it.total<min){
        	min = it.total
            minTimestamp = timestamp
        }
    }
    return minTimestamp.substring(11,13)
}

def MinValue(List values){
	def min = 1000
    def minTimestamp = ""
	values.each{
    	def timestamp = it.startsAt
        def total = it.total   
        if(it.total<min){
        	min = it.total
            minTimestamp = timestamp
        }
    }
    return min
}

def MedValue(List values){
    //log.debug("MedValue")
	def med = 0
	values.each{
        med = med + it.total
    }
    med = med / values.size
    //log.debug("   med" + med)
    return med
}

def PriceNextHours(List values){
	def priceNowTimestamp = 0
    def priceNextHour = -1;
    def priceNextNextHour = -1;
    def i=0
    values.each{
        Calendar cal=Calendar.getInstance();
        def hourNowUtc = cal.get(Calendar.HOUR_OF_DAY) + 1
        def dayNowUtc = cal.get(Calendar.DAY_OF_MONTH)    
        def startsAt = it.startsAt
        def total = it.total        
        int hourNow = startsAt.substring(11,13) as int
        int dayNow = startsAt.substring(8,10) as int
        int hourOffset = startsAt.substring(20,22) as int
        def timeZoneOperator = startsAt.substring(19,20)
        if(timeZoneOperator=="+"){
            hourNowUtc = hourNowUtc + hourOffset
        }
        if(timeZoneOperator=="-"){
            hourNowUtc = hourNowUtc - hourOffset
        }
        if(hourNowUtc<0){
        	hourNowUtc = hourNowUtc+24 //wrap
            dayNowUtc = dayNowUtc-1
        }
        if(hourNowUtc>23){
        	hourNowUtc = hourNowUtc-24 //wrap
            dayNowUtc = dayNowUtc+1
        }
        if(hourNow == hourNowUtc && dayNow == dayNowUtc ){
        	priceNextHour = it.total
            priceNextNextHour = values[i+1].total   
            priceNowTimestamp = hourNowUtc
        }
    	i++

    }
    
    def priceNextTimestamp = 0
    if(priceNowTimestamp<23)
    	priceNextTimestamp = priceNowTimestamp + 1
    return [priceNextHour, priceNextNextHour, fromToTimestamp(priceNowTimestamp), fromToTimestamp(priceNextTimestamp)]
}

def fromToTimestamp(def timestamp){
	def from = timestamp
    def to = timestamp + 1
    if(to>23){
    	to = 0
    }
    return "${formatTimestamp(from)} - ${formatTimestamp(to)} "
}

def formatTimestamp(def timestamp){
	if(timestamp < 9)
    	return "0${timestamp}"
    return timestamp
}