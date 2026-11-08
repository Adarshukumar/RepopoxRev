// Read-only evidence collector. No blocking APIs, CSS changes, response changes,
// media seeking, ad skipping or player manipulation. Activated only by Android tests.
setInterval(() => {
    const video = document.querySelector('video');
    const play = document.querySelector('.ytp-large-play-button, button[aria-label="Play"]');
    const rect = (play || video)?.getBoundingClientRect();
    const quality = video?.getVideoPlaybackQuality?.();
    browser.runtime.sendNativeMessage('frameEvidence', {
        url: location.origin + location.pathname,
        title: document.title,
        time: video?.currentTime || 0,
        paused: video?.paused ?? true,
        readyState: video?.readyState || 0,
        frames: quality?.totalVideoFrames || 0,
        adMarker: !!document.querySelector('.ad-showing, .ad-interrupting'),
        width: innerWidth,
        height: innerHeight,
        tapX: rect ? rect.x + rect.width / 2 : -1,
        tapY: rect ? rect.y + rect.height / 2 : -1
    }).catch(error => console.warn("FrameEvidence", String(error)));
}, 1000);
