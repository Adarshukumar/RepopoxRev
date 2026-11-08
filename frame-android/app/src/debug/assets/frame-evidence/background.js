// Content scripts cannot directly use native messaging on this Gecko release.
// Relay read-only measurements through the extension's authorized background.
browser.runtime.onMessage.addListener((message, sender) => {
    if (sender.id !== browser.runtime.id || !sender.url) return;
    const host = new URL(sender.url).hostname;
    if (host !== 'youtube.com' && !host.endsWith('.youtube.com')) return;
    return browser.runtime.sendNativeMessage('frameEvidence', message);
});
