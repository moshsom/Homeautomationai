# Chatomation

AI-powered home automations for [Hubitat Elevation](https://hubitat.com/) through natural conversation. Just describe what you want in plain English and the AI builds the automation for you.

**Example:**
> "I want the kitchen light to turn on when there is motion in the kitchen after 10pm but I want it to be at 20%. Then after 10 mins of no motion it turns the lights back off."

No rules to configure. No conditions to set up. Just talk.

## Features

- **Natural language** — describe automations the way you'd explain them to a person
- **Conversational** — each automation has its own chat thread; the AI asks clarifying questions if needed
- **OpenAI & Claude** — choose GPT or Claude and plug in your own API key
- **Runs locally** — the AI is only used when creating or modifying an automation; once built, the rule executes entirely on your hub with zero API calls
- **Full device support** — lights, dimmers, color bulbs, motion sensors, contact sensors, locks, thermostats, fans, shades, garage doors, valves, alarms, speakers, and more
- **Conditions** — time of day, hub modes, device states, days of the week
- **Delays** — "turn off after 10 minutes of no motion" just works
- **Sunrise / Sunset** — schedule actions relative to sun events with optional offsets
- **Enable / Disable** — pause and resume automations like Rule Machine
- **Modify by chat** — jump back into the conversation to change anything
- **Error notifications** — logs everything and sends push alerts when something fails

## Requirements

- Hubitat Elevation hub (any current firmware)
- An API key from **OpenAI** or **Anthropic**
  - OpenAI: https://platform.openai.com/api-keys
  - Anthropic: https://console.anthropic.com/settings/keys

## Installation

### Step 1 — Install the parent app

1. In your Hubitat admin, go to **Apps Code**
2. Click **New App**
3. Copy the entire contents of [`chatomation.groovy`](chatomation.groovy) and paste it in
4. Click **Save**

### Step 2 — Install the child app

1. Still in **Apps Code**, click **New App** again
2. Copy the entire contents of [`chatomation-automation.groovy`](chatomation-automation.groovy) and paste it in
3. Click **Save**

### Step 3 — Add the app

1. Go to **Apps** → **Add User App**
2. Select **Chatomation**
3. Configure the settings:
   - **AI Provider** — choose OpenAI or Anthropic
   - **API Key** — paste your key
   - **Model** — pick a model (gpt-4o or claude-sonnet recommended)
4. Under **Devices**, select all the devices you want Chatomation to have access to
5. Optionally select a **Notification Device** for error alerts (e.g., the Hubitat mobile app)
6. Click **Done**

## Creating Your First Automation

1. Open **Chatomation** from your Apps list
2. Click **Create New Automation**
3. Click **Open Chat**
4. Type what you want in plain English, for example:
   > Turn on the porch light at sunset and off at sunrise.
5. The AI will either create the automation immediately or ask a clarifying question
6. Once the rule is generated, you'll see a summary of what it does
7. Click **Done** — the automation is now running

## Managing Automations

| Action | How |
|--------|-----|
| **Pause / Resume** | Open the automation → toggle **Enable Automation** |
| **Modify** | Open the automation → **Open Chat** → describe the change |
| **Delete** | Open the automation → scroll to bottom → **Remove** |
| **View rule details** | Open the automation → the JSON rule is shown at the bottom |

## Supported Triggers

| Trigger | Example |
|---------|---------|
| Device event | "When there's motion in the hallway..." |
| Time schedule | "Every day at 7am..." |
| Sunrise / Sunset | "At sunset..." or "30 minutes before sunrise..." |
| Mode change | "When the hub switches to Night mode..." |

## Supported Conditions

| Condition | Example |
|-----------|---------|
| Time window | "...only after 10pm" |
| Hub mode | "...only in Away mode" |
| Device state | "...only if the front door is locked" |
| Day of week | "...only on weekdays" |

## Tips

- **Be specific about device names** — the AI sees your actual device names, so use them naturally. If you have two "Kitchen Light" devices, the AI will ask which one you mean.
- **Combine triggers and conditions freely** — "When the garage door opens and it's after dark, turn on the driveway lights and send me a notification."
- **Modify anytime** — open the chat and say "change the brightness to 50%" or "also turn on the hallway light."
- **Cost** — API calls only happen when you chat. A typical automation costs a fraction of a cent to create. Once running, there are zero API costs.

## Files

| File | Description |
|------|-------------|
| `chatomation.groovy` | Parent app — settings, device selection, AI API integration |
| `chatomation-automation.groovy` | Child app — chat UI, rule engine, event handling |

## License

MIT
