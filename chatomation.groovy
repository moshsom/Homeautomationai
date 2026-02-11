/**
 *  Chatomation - Parent App
 *
 *  AI-powered home automations through natural conversation.
 *  Describe what you want in plain English and the AI builds
 *  the automation for you.
 *
 *  Supports OpenAI (GPT) and Anthropic (Claude) APIs.
 */

definition(
    name: "Chatomation",
    namespace: "chatomation",
    author: "Chatomation",
    description: "AI-powered home automations through natural conversation",
    category: "Automation",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Chatomation", install: true, uninstall: true) {

        section("<b>AI Configuration</b>") {
            input "aiProvider", "enum", title: "AI Provider",
                options: ["OpenAI", "Anthropic"], required: true, submitOnChange: true
            input "apiKey", "password", title: "API Key", required: true

            if (aiProvider == "OpenAI") {
                input "aiModel", "enum", title: "Model",
                    options: ["gpt-4o", "gpt-4o-mini", "gpt-4-turbo"],
                    defaultValue: "gpt-4o", required: true
            } else if (aiProvider == "Anthropic") {
                input "aiModel", "enum", title: "Model",
                    options: [
                        "claude-sonnet-4-5-20250929",
                        "claude-opus-4-6",
                        "claude-haiku-4-5-20251001"
                    ],
                    defaultValue: "claude-sonnet-4-5-20250929", required: true
            }
        }

        section("<b>Devices</b>") {
            input "useAllDevices", "bool",
                title: "Grant access to ALL devices (recommended)",
                defaultValue: true, submitOnChange: true

            if (useAllDevices) {
                paragraph "Select all devices from the two lists below. " +
                    "Actuators covers anything controllable (lights, switches, " +
                    "locks, thermostats, etc.). Sensors covers anything that " +
                    "reports data (motion, contact, temperature, etc.). " +
                    "Many devices appear in both — that's normal."
                input "allActuators", "capability.actuator",
                    title: "All Actuators (lights, switches, locks, etc.)",
                    multiple: true, required: false
                input "allSensors", "capability.sensor",
                    title: "All Sensors (motion, contact, temperature, etc.)",
                    multiple: true, required: false
            } else {
                paragraph "Select devices by category. Only selected devices " +
                    "will be available to the AI."
                input "lights", "capability.switch",
                    title: "Switches & Lights", multiple: true, required: false
                input "dimmers", "capability.switchLevel",
                    title: "Dimmers", multiple: true, required: false
                input "colorLights", "capability.colorControl",
                    title: "Color Lights", multiple: true, required: false
                input "colorTempLights", "capability.colorTemperature",
                    title: "Color Temperature Lights", multiple: true, required: false
                input "motionSensors", "capability.motionSensor",
                    title: "Motion Sensors", multiple: true, required: false
                input "contactSensors", "capability.contactSensor",
                    title: "Contact Sensors (Doors/Windows)", multiple: true, required: false
                input "tempSensors", "capability.temperatureMeasurement",
                    title: "Temperature Sensors", multiple: true, required: false
                input "humiditySensors", "capability.relativeHumidityMeasurement",
                    title: "Humidity Sensors", multiple: true, required: false
                input "illuminanceSensors", "capability.illuminanceMeasurement",
                    title: "Light Level Sensors", multiple: true, required: false
                input "locks", "capability.lock",
                    title: "Locks", multiple: true, required: false
                input "thermostats", "capability.thermostat",
                    title: "Thermostats", multiple: true, required: false
                input "presenceSensors", "capability.presenceSensor",
                    title: "Presence Sensors", multiple: true, required: false
                input "waterSensors", "capability.waterSensor",
                    title: "Water / Leak Sensors", multiple: true, required: false
                input "shades", "capability.windowShade",
                    title: "Window Shades / Blinds", multiple: true, required: false
                input "garageDoors", "capability.garageDoorControl",
                    title: "Garage Doors", multiple: true, required: false
                input "fans", "capability.fanControl",
                    title: "Fans", multiple: true, required: false
                input "alarms", "capability.alarm",
                    title: "Alarms / Sirens", multiple: true, required: false
                input "speakers", "capability.speechSynthesis",
                    title: "Speech Devices", multiple: true, required: false
                input "valves", "capability.valve",
                    title: "Valves", multiple: true, required: false
            }
        }

        section("<b>Notifications</b>") {
            input "notifyDevices", "capability.notification",
                title: "Notification Devices (for error alerts)",
                multiple: true, required: false
        }

        section("<b>Logging</b>") {
            input "logLevel", "enum", title: "Log Level",
                options: ["Info", "Debug", "Trace"],
                defaultValue: "Info", required: true
        }

        section("<b>Automations</b>") {
            app(name: "automations", appName: "Chatomation Automation",
                namespace: "chatomation", title: "Create New Automation",
                multiple: true)
        }
    }
}

// ---------------------------------------------------------------------------
//  Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    logInfo "Chatomation installed"
    initialize()
}

def updated() {
    logInfo "Chatomation updated"
    initialize()
}

def initialize() {
    logInfo "Chatomation initialized with provider=${aiProvider}, model=${aiModel}"
}

// ---------------------------------------------------------------------------
//  Device helpers (called by child apps)
// ---------------------------------------------------------------------------

def getAllDevices() {
    def allDevs = []

    if (useAllDevices) {
        // Broad selection: actuators + sensors covers virtually everything
        if (allActuators) allDevs.addAll(allActuators)
        if (allSensors)   allDevs.addAll(allSensors)
    } else {
        // Per-category selection
        [lights, dimmers, colorLights, colorTempLights,
         motionSensors, contactSensors, tempSensors, humiditySensors,
         illuminanceSensors, locks, thermostats, presenceSensors,
         waterSensors, shades, garageDoors, fans, alarms, speakers, valves
        ].each { devList ->
            if (devList) allDevs.addAll(devList)
        }
    }

    return allDevs.unique { it.id }
}

def getDeviceById(deviceId) {
    return getAllDevices().find { it.id.toString() == deviceId.toString() }
}

// Build a text description of every device for the AI system prompt.
def buildDeviceContext() {
    def devices = getAllDevices()
    if (!devices) return "No devices have been selected in the Chatomation parent app.\n"

    def sb = new StringBuilder()
    devices.each { dev ->
        sb.append("- ${dev.displayName}  (ID: ${dev.id})\n")
        sb.append("    Capabilities: ${dev.capabilities.collect { it.name }.join(', ')}\n")

        def cmds = dev.supportedCommands.collect { cmd ->
            def args = cmd.arguments.collect { it.name }.join(', ')
            args ? "${cmd.name}(${args})" : "${cmd.name}()"
        }.join(', ')
        sb.append("    Commands: ${cmds}\n")

        // Include a handful of key current-state attributes
        def keyAttrs = ['switch', 'level', 'colorTemperature', 'hue', 'saturation',
                        'motion', 'contact', 'temperature', 'humidity', 'illuminance',
                        'lock', 'thermostatMode', 'heatingSetpoint', 'coolingSetpoint',
                        'presence', 'water', 'windowShade', 'door', 'speed',
                        'alarm', 'valve', 'battery']
        def stateItems = []
        keyAttrs.each { attr ->
            def val = dev.currentValue(attr)
            if (val != null) stateItems << "${attr}=${val}"
        }
        if (stateItems) {
            sb.append("    State: ${stateItems.join(', ')}\n")
        }
        sb.append("\n")
    }
    return sb.toString()
}

// ---------------------------------------------------------------------------
//  Hub helpers
// ---------------------------------------------------------------------------

def getHubModes() {
    return location.modes.collect { it.name }
}

def getCurrentMode() {
    return location.mode
}

// ---------------------------------------------------------------------------
//  AI API  (called by child apps)
// ---------------------------------------------------------------------------

def callAI(String systemPrompt, List messages) {
    if (!apiKey) {
        logError "No API key configured"
        return null
    }
    if (aiProvider == "OpenAI") {
        return callOpenAI(systemPrompt, messages)
    } else if (aiProvider == "Anthropic") {
        return callAnthropic(systemPrompt, messages)
    }
    logError "Unknown AI provider: ${aiProvider}"
    return null
}

private callOpenAI(String systemPrompt, List messages) {
    def allMessages = [[role: "system", content: systemPrompt]] + messages

    def requestBody = [
        model      : aiModel ?: "gpt-4o",
        messages   : allMessages,
        max_tokens : 4096,
        temperature: 0.7
    ]

    def jsonBody = groovy.json.JsonOutput.toJson(requestBody)
    logDebug "OpenAI request: model=${requestBody.model}, messages=${allMessages.size()}, bodySize=${jsonBody.size()}"

    def params = [
        uri    : "https://api.openai.com/v1/chat/completions",
        headers: [
            "Authorization": "Bearer ${apiKey}",
            "Content-Type" : "application/json"
        ],
        body   : jsonBody
    ]

    def responseText = null
    try {
        httpPost(params) { resp ->
            logDebug "OpenAI response status: ${resp.status}"
            def respData = resp.data
            // Handle both auto-parsed JSON and raw string responses
            if (respData instanceof String) {
                respData = new groovy.json.JsonSlurper().parseText(respData)
            }
            responseText = respData?.choices?.getAt(0)?.message?.content
            if (!responseText) {
                logError "OpenAI response missing expected data: ${respData}"
            }
        }
    } catch (e) {
        logError "OpenAI API call failed: ${e.getClass().getSimpleName()}: ${e.message}"
        try {
            if (e.response?.data) {
                logError "OpenAI error body: ${e.response.data}"
            }
        } catch (ignored) {}
    }
    return responseText
}

private callAnthropic(String systemPrompt, List messages) {
    def requestBody = [
        model     : aiModel ?: "claude-sonnet-4-5-20250929",
        max_tokens: 4096,
        system    : systemPrompt,
        messages  : messages
    ]

    def jsonBody = groovy.json.JsonOutput.toJson(requestBody)
    logDebug "Anthropic request: model=${requestBody.model}, messages=${messages.size()}, bodySize=${jsonBody.size()}"

    def params = [
        uri    : "https://api.anthropic.com/v1/messages",
        headers: [
            "x-api-key"        : apiKey,
            "anthropic-version" : "2023-06-01",
            "Content-Type"      : "application/json"
        ],
        body   : jsonBody
    ]

    def responseText = null
    try {
        httpPost(params) { resp ->
            logDebug "Anthropic response status: ${resp.status}"
            def respData = resp.data
            if (respData instanceof String) {
                respData = new groovy.json.JsonSlurper().parseText(respData)
            }
            responseText = respData?.content?.getAt(0)?.text
            if (!responseText) {
                logError "Anthropic response missing expected data: ${respData}"
            }
        }
    } catch (e) {
        logError "Anthropic API call failed: ${e.getClass().getSimpleName()}: ${e.message}"
        try {
            if (e.response?.data) {
                logError "Anthropic error body: ${e.response.data}"
            }
        } catch (ignored) {}
    }
    return responseText
}

// ---------------------------------------------------------------------------
//  Notifications
// ---------------------------------------------------------------------------

def sendNotification(String message) {
    if (notifyDevices) {
        notifyDevices.each { it.deviceNotification(message) }
    }
    logInfo "Notification: ${message}"
}

// ---------------------------------------------------------------------------
//  Logging helpers
// ---------------------------------------------------------------------------

private logInfo(msg)  { log.info  "Chatomation: ${msg}" }
private logDebug(msg) { if (logLevel in ["Debug","Trace"]) log.debug "Chatomation: ${msg}" }
private logTrace(msg) { if (logLevel == "Trace") log.trace "Chatomation: ${msg}" }
private logError(msg) { log.error "Chatomation: ${msg}" }
