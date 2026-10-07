# Assistant actions and voice

AI Interaction (menu bar) accepts natural-language trade and bot requests and the commands shown by `/help`.
Desktop and authorized Telegram users share the existing command handlers, with independent user-bound previews.
Market, limit, stop, trailing-stop and bracket orders and cancellation use the selected exchange's execution provider.
Unsupported exchange order types return an error rather than a fabricated success. Native CDE IDs are preserved.
`/botstart` starts the desktop bot workflow after confirmation; `/botstop` requests its stop workflow.
`/pause` disables automatic trading; `/resume` requests re-enabling it, subject to health checks and confirmation.

Trade actions, cancellation and bot starts/resumes return an exchange/mode-specific preview.
Enter `/confirm CODE` yourself within 60 seconds, or `/abort`. The model cannot confirm actions.
Confirmations are consumed before submission and cannot replay. Check `/orders` or `/history` after an uncertain result.
Existing authentication, trading mode and local paper execution rules apply.

Click **Speak**, then **Finish recording** to upload microphone audio to OpenAI transcription.
Recording is capped at 60 seconds. Review/edit the transcript and click Send; transcription never automatically sends a command.
**Listen to reply** plays the last reply; **Read replies aloud** enables automatic playback (on by default); **Stop audio** stops playback.
Audio uses the configured OpenAI key, `gpt-4o-mini-transcribe`, and `gpt-4o-mini-tts` with PCM output and the coral voice.
The voice is AI-generated. Speech services need account access to these models and a working microphone/speaker.
Audio remains in memory; no local recording files are written. Spoken output is limited to the first 4,000 characters.
Voice controls are in the desktop panel; Telegram supports text commands and natural-language action requests.

Q&A remains available while bot trading is stopped. Connect a desktop exchange to enable the action bridge.

## Screenshots

Click **Take screenshot** to capture the main InvestPro window and preview it in the AI panel.
Add a question and click Send to submit the image to OpenAI for analysis, or use **Send screenshot to Telegram**
to upload it to the configured notification chat. **Remove screenshot** discards the attachment.
Capture works independently of bot trading. It captures the application scene, not other desktop applications.
PNG attachments are limited to 10 MB. OpenAI receives the image only when Send is clicked; screenshot analysis has no action tools.
The image is not retained in subsequent conversation turns. Telegram uploads use a temporary file that is removed afterward.
`/screenshot` also works from desktop AI commands and the configured, authorized Telegram chat.
