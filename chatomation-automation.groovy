/**
 *  Chatomation Automation - Child App
 *
 *  Each instance represents a single AI-created automation with its own
 *  conversation thread.  The user describes what they want in plain English,
 *  the AI asks clarifying questions if needed, then generates a structured
 *  rule.  The parent app handles all event subscriptions and command execution.
 */

definition(
    name: "Chatomation Automation",
    namespace: "chatomation",
    author: "Chatomation",
    description: "Individual AI-powered automation",
    category: "Automation",
    parent: "chatomation:Chatomation",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "mainPage")
    page(name: "chatPage")
}

// ===========================================================================
//  UI Pages
// ===========================================================================

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {

        section("<h2>${state.automationName ?: 'New Automation'}</h2>") {
            input "automationEnabled", "bool", title: "Enable Automation",
                defaultValue: true, submitOnChange: true
        }

        if (state.ruleDescription) {
            section("<b>What this automation does</b>") {
                paragraph state.ruleDescription
                def status = (automationEnabled != false) ? "Active" : "Paused"
                paragraph "<i>Status: ${status}</i>"
            }
        }

        section() {
            href "chatPage", title: "Open Chat",
                description: state.ruleDescription ?
                    "Modify this automation through conversation" :
                    "Describe the automation you want to create"
        }

        if (state.currentRule) {
            section("<b>Rule Details</b>") {
                def ruleText = groovy.json.JsonOutput.prettyPrint(
                    groovy.json.JsonOutput.toJson(state.currentRule))
                paragraph "<pre style='font-size:12px;'>${ruleText}</pre>"
            }
        }
    }
}

def chatPage() {
    dynamicPage(name: "chatPage", title: "Chatomation Chat", install: false, uninstall: false) {

        // ---- Conversation history ----
        section() {
            if (state.conversation) {
                def msgs = state.conversation
                def recentCount = 6
                def hasOlder = msgs.size() > recentCount
                def olderMsgs = hasOlder ? msgs[0..-(recentCount + 1)] : []
                def recentMsgs = hasOlder ? msgs[-(recentCount)..-1] : msgs

                // Collapsed older messages
                if (hasOlder) {
                    def olderHtml = "<details style='margin-bottom:8px;'>" +
                        "<summary style='cursor:pointer;color:#666;font-size:13px;" +
                        "padding:6px 0;'>Show ${olderMsgs.size()} earlier messages</summary>"
                    olderMsgs.each { msg ->
                        olderHtml += renderMessage(msg)
                    }
                    olderHtml += "</details>"
                    paragraph olderHtml
                }

                // Recent messages always visible
                recentMsgs.each { msg ->
                    paragraph renderMessage(msg)
                }
            }

            if (state.lastError) {
                paragraph "<div style='background:#ffebee;padding:10px;border-radius:8px;" +
                    "margin:6px 0;border-left:4px solid #f44336;'>" +
                    "<b>Error:</b> ${state.lastError}</div>"
                state.lastError = null
            }
        }

        // ---- Message input + spinner ----
        section() {
            // CSS spinner animation
            paragraph "<style>@keyframes chatomation-spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>"

            input "userMessage", "text", title: "Your message",
                required: false, submitOnChange: false
            input "sendMessage", "button", title: "Send"

            // Spinner + help text shown via JS when Send is clicked
            paragraph "<div id='chatomation-wait' style='display:none;margin-top:8px;'>" +
                "<span style='display:inline-block;width:18px;height:18px;" +
                "border:3px solid #ddd;border-top:3px solid #2196F3;border-radius:50%;" +
                "animation:chatomation-spin 1s linear infinite;vertical-align:middle;'></span>" +
                " <span style='color:#555;font-size:14px;vertical-align:middle;'>AI is thinking...</span></div>" +
                "<div id='chatomation-hint' style='color:#888;font-size:13px;margin-top:4px;'>" +
                "After clicking Send, please wait for the AI to respond.</div>" +
                "<script>document.querySelector('[name=sendMessage]')?.addEventListener('click',function(){" +
                "var w=document.getElementById('chatomation-wait');" +
                "if(w)w.style.display='block';" +
                "var h=document.getElementById('chatomation-hint');" +
                "if(h)h.style.display='none';});</script>"
        }
    }
}

// ===========================================================================
//  Button handler
// ===========================================================================

def appButtonHandler(String btn) {
    if (btn == "sendMessage") {
        def msg = settings.userMessage?.trim()
        if (msg) {
            processUserMessage(msg)
            app.updateSetting("userMessage", [type: "text", value: ""])
        }
    }
}

// ===========================================================================
//  Conversation + AI
// ===========================================================================

private processUserMessage(String message) {
    // Always read into a local variable and reassign state — direct mutation
    // of nested state objects is not persisted by the Hubitat runtime.
    def conv = state.conversation ?: []

    // Append user message with timestamp
    def now = new Date().format("yyyy-MM-dd h:mm a")
    conv << [role: "user", content: message, ts: now]

    // Call AI (pass local copy so the user message is included)
    def systemPrompt = buildSystemPrompt()
    def response = parent.callAI(systemPrompt, conv)

    if (response) {
        def respTime = new Date().format("yyyy-MM-dd h:mm a")
        conv << [role: "assistant", content: response, ts: respTime]
        state.conversation = conv

        // Look for a rule JSON in the response
        def ruleJson = extractRuleJson(response)
        if (ruleJson) {
            activateRule(ruleJson)
        }

        trimConversation()
    } else {
        def errTime = new Date().format("yyyy-MM-dd h:mm a")
        conv << [role: "assistant", ts: errTime,
            content: "Sorry, I could not reach the AI service. " +
                     "Please check your API key in the Chatomation parent app settings " +
                     "and try again. Check Hubitat Logs for detailed error info."]
        state.conversation = conv
        state.lastError = "AI API call returned no response. " +
            "Open Hubitat Logs (gear icon → Logs) and look for 'Chatomation' errors."
    }
}

private trimConversation() {
    def max = 40
    def conv = state.conversation
    if (conv && conv.size() > max) {
        def keep = max - 2
        state.conversation = conv[0..1] + conv[-(keep)..-1]
    }
}

// ===========================================================================
//  System prompt
// ===========================================================================

private buildSystemPrompt() {
    def deviceCtx   = parent.buildDeviceContext()
    def modes       = parent.getHubModes()?.join(', ') ?: 'Default'
    def currentMode = parent.getCurrentMode() ?: 'Default'
    def existingRule = state.currentRule ?
        groovy.json.JsonOutput.toJson(state.currentRule) : "None yet"

    return """\
You are Chatomation, a friendly AI assistant that creates home automations for a Hubitat Elevation smart home hub. Have a natural conversation with the user to understand what they want, then generate a structured rule the hub can execute.

== AVAILABLE DEVICES ==
${deviceCtx}
== HUB MODES ==
Available: ${modes}
Current: ${currentMode}

== EXISTING RULE FOR THIS AUTOMATION ==
${existingRule}

== RULE JSON FORMAT ==
When you have enough information, output the rule inside a fenced code block:

```json
{
  "name": "Short name",
  "description": "Plain-English description of the automation",
  "actions": [
    {
      "trigger": { ... },
      "conditions": [ ... ],
      "commands": [ ... ],
      "delay": null,
      "cancelPendingDelay": false
    }
  ]
}
```

--- Trigger objects ---
Device event   : {"type":"device","deviceId":<id>,"attribute":"<attr>","value":"<val>"}
Scheduled time : {"type":"time","time":"HH:mm"}
Sunrise        : {"type":"sunrise","offset":<minutes>}   (negative = before)
Sunset         : {"type":"sunset","offset":<minutes>}
Mode change    : {"type":"mode","value":"<mode name>"}

--- Condition objects (all must be true) ---
Time window    : {"type":"time","after":"HH:mm","before":"HH:mm"}
Hub mode       : {"type":"mode","values":["Mode1","Mode2"]}
Device state   : {"type":"device","deviceId":<id>,"attribute":"<attr>","value":"<val>"}
Day of week    : {"type":"dayOfWeek","days":["Monday","Friday"]}

--- Command objects ---
{"deviceId":<id>,"command":"<cmd>","args":[<optional arguments>]}

Common commands:
  switch        : on(), off()
  dimmer        : setLevel(0-100), on(), off()
  color light   : setColor(hue 0-100, saturation 0-100, level 0-100), setColorTemperature(kelvin)
  lock          : lock(), unlock()
  thermostat    : setHeatingSetpoint(temp), setCoolingSetpoint(temp), setThermostatMode(mode)
  valve         : open(), close()
  shade         : open(), close(), setPosition(0-100)
  alarm         : strobe(), siren(), both(), off()
  garage door   : open(), close()
  fan           : setSpeed("low"/"medium-low"/"medium"/"medium-high"/"high"/"on"/"off"/"auto"), on(), off()
  speech        : speak("text")

--- Delays & cancellation ---
- "delay": <minutes>  makes the commands execute after a wait.
- "cancelPendingDelay": true  on another action cancels any pending delayed
  commands that target the same devices.
  Example — motion lighting:
    Action 1: motion active  -> lights on, cancelPendingDelay: true
    Action 2: motion inactive -> lights off, delay: 10

== GUIDELINES ==
1. Always reference devices by their numeric ID from the device list.
2. Use display names when talking to the user.
3. If the request is ambiguous, ask ONE clarifying question.
4. After outputting the JSON rule, add a short plain-English summary.
5. To modify an existing rule, output a complete replacement JSON.
6. Keep responses concise and conversational.
"""
}

// ===========================================================================
//  Rule parsing
// ===========================================================================

private extractRuleJson(String text) {
    def matcher = text =~ /(?s)```json\s*(\{.*?\})\s*```/
    if (matcher.find()) {
        try {
            return new groovy.json.JsonSlurper().parseText(matcher.group(1))
        } catch (e) {
            log.error "Chatomation: failed to parse rule JSON: ${e.message}"
            state.lastError = "The AI returned invalid JSON. Please try again."
        }
    }
    return null
}

// ===========================================================================
//  Rule activation — registers rule with parent for execution
// ===========================================================================

private activateRule(Map rule) {
    log.info "Chatomation: activating rule '${rule.name}'"

    state.currentRule      = rule
    state.automationName   = rule.name
    state.ruleDescription  = rule.description

    app.updateLabel(rule.name ?: "Chatomation Automation")

    // Register rule with parent — parent handles all subscriptions and execution
    def enabled = (automationEnabled != false)
    parent.registerChildRule(app.id.toString(), rule, enabled)
}

// ===========================================================================
//  Lifecycle
// ===========================================================================

def installed() {
    log.info "Chatomation Automation installed"
    // Guard: Hubitat can call installed() on existing children when a new
    // sibling is added via the parent page. Only initialise state that is
    // not already set so existing conversations are never wiped.
    if (!state.conversation) {
        state.conversation = [
            [role: "assistant",
             ts: new Date().format("yyyy-MM-dd h:mm a"),
             content: "Hi! I'm Chatomation. Describe the automation you'd like " +
                      "to create and I'll set it up for you.\n\n" +
                      "For example:\n" +
                      "- \"Turn on the porch light at sunset and off at sunrise.\"\n" +
                      "- \"When the front door opens, send me a notification.\"\n" +
                      "- \"If there's motion in the kitchen after 10 PM, turn the " +
                      "light on at 20 %. Turn it off after 10 minutes of no motion.\""]
        ]
    }
    if (state.enabled == null) state.enabled = true
}

def updated() {
    log.info "Chatomation Automation updated"
    // Hubitat may call updated() instead of (or before) installed() for new
    // child apps, so initialise the greeting here too if not yet set.
    if (!state.conversation) {
        state.conversation = [
            [role: "assistant",
             ts: new Date().format("yyyy-MM-dd h:mm a"),
             content: "Hi! I'm Chatomation. Describe the automation you'd like " +
                      "to create and I'll set it up for you.\n\n" +
                      "For example:\n" +
                      "- \"Turn on the porch light at sunset and off at sunrise.\"\n" +
                      "- \"When the front door opens, send me a notification.\"\n" +
                      "- \"If there's motion in the kitchen after 10 PM, turn the " +
                      "light on at 20 %. Turn it off after 10 minutes of no motion.\""]
        ]
    }
    state.enabled = (automationEnabled != false)

    // Update parent with current enabled state
    if (state.currentRule) {
        parent.setChildRuleEnabled(app.id.toString(), state.enabled)
    }
}

def uninstalled() {
    // Tell parent to remove our rule and rebuild subscriptions
    parent.unregisterChildRule(app.id.toString())
    log.info "Chatomation Automation uninstalled"
}

// ===========================================================================
//  Helpers
// ===========================================================================

private renderMessage(Map msg) {
    def timestamp = msg.ts ? "<span style='color:#999;font-size:11px;float:right;'>${msg.ts}</span>" : ""
    if (msg.role == "user") {
        return "<div style='background:#f5f5f5;padding:10px 14px;" +
            "border-radius:10px;margin:6px 0;border-left:4px solid #2196F3;'>" +
            "${timestamp}<b>You:</b><br/>${escapeHtml(msg.content)}</div>"
    } else if (msg.role == "assistant") {
        def display = msg.content.replaceAll(
            /(?s)```json\s*\{.*?\}\s*```/,
            '<i>[automation rule generated]</i>')
        return "<div style='background:#e3f2fd;padding:10px 14px;" +
            "border-radius:10px;margin:6px 0;border-left:4px solid #4CAF50;'>" +
            "${timestamp}<b>Chatomation:</b><br/>${display}</div>"
    }
    return ""
}

private escapeHtml(String text) {
    if (!text) return ""
    return text.replaceAll("&", "&amp;")
               .replaceAll("<", "&lt;")
               .replaceAll(">", "&gt;")
               .replaceAll("\n", "<br/>")
}

private logDebug(msg) {
    if (parent?.getSetting("logLevel") in ["Debug", "Trace"]) {
        log.debug "Chatomation [${state.automationName ?: app.label}]: ${msg}"
    }
}
