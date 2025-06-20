// HLSPlayer.jsx
import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';

const HLSPlayer = ({ streamId }) => {
    const videoRef = useRef(null);
    const hlsInstanceRef = useRef(null);

    // IMPORTANT: Add this check to prevent attempting to load 'undefined' streamId
    if (!streamId) {
        console.warn("HLSPlayer: streamId is undefined or empty, cannot load HLS source.");
        return (
            <div style={{ textAlign: 'center', color: '#ccc', padding: '20px' }}>
                <p>No stream ID available for playback.</p>
                <p>Please start a broadcast or enter a valid Stream ID.</p>
            </div>
        );
    }

    const HLS_URL = `http://localhost:8082/hls/${streamId}/index.m3u8`;

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        // Destroy existing Hls instance if any, especially on streamId change
        if (hlsInstanceRef.current) {
            hlsInstanceRef.current.destroy();
            hlsInstanceRef.current = null;
            console.log("Destroying previous Hls instance for cleanup or streamId change.");
        }

        if (Hls.isSupported()) {
            console.log("hls.js is supported. Initializing Hls instance for stream:", streamId);
            const hls = new Hls();
            hlsInstanceRef.current = hls;

            hls.attachMedia(video);

            const checkAndLoadHls = () => {
                fetch(HLS_URL, { method: 'HEAD' }) // HEAD request is lighter
                    .then(response => {
                        if (response.ok) {
                            console.log(`HLS manifest found for ${streamId}. Loading source: ${HLS_URL}`);
                            hls.loadSource(HLS_URL);
                        } else if (response.status === 404) {
                            console.log(`HLS manifest for ${streamId} not yet available (404), retrying...`);
                            setTimeout(checkAndLoadHls, 2000); // Retry after 2 seconds
                        } else {
                            console.error(`Error checking HLS manifest for ${streamId}: Status ${response.status}`);
                            // Consider stopping retries or increasing delay on non-404 errors
                            setTimeout(checkAndLoadHls, 5000); // Longer retry for other errors
                        }
                    })
                    .catch(error => {
                        console.error(`Network error checking HLS manifest for ${streamId}:`, error);
                        setTimeout(checkAndLoadHls, 3000); // Retry after 3 seconds on network error
                    });
            };

            checkAndLoadHls(); // Start the check/load process

            hls.on(Hls.Events.MANIFEST_PARSED, function (event, data) {
                console.log('HLS Manifest Parsed:', data);
                // video.play(); // Auto-play if desired and allowed by browser
            });

            hls.on(Hls.Events.ERROR, function (event, data) {
                console.error('Fatal HLS.js error:', data);
                if (data.fatal) {
                    switch (data.details) {
                        case Hls.ErrorDetails.MANIFEST_LOAD_ERROR:
                        case Hls.ErrorDetails.MANIFEST_PARSE_ERROR:
                        case Hls.ErrorDetails.LEVEL_LOAD_ERROR:
                        case Hls.ErrorDetails.FRAG_LOAD_ERROR:
                            console.warn("Retrying HLS load due to fatal error:", data.details);
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy(); // Destroy current instance
                                hlsInstanceRef.current = null;
                            }
                            // Re-initialize and retry (this useEffect will re-run due to streamId dependency if it changes)
                            // Or, more robustly, call checkAndLoadHls after a delay
                            setTimeout(() => {
                                if (Hls.isSupported() && videoRef.current) {
                                    console.log("Attempting to re-initialize Hls instance after error.");
                                    const newHls = new Hls();
                                    hlsInstanceRef.current = newHls;
                                    newHls.attachMedia(videoRef.current);
                                    newHls.loadSource(HLS_URL); // Try loading immediately
                                    // Re-add error handlers for the new instance if needed
                                    newHls.on(Hls.Events.ERROR, (e, d) => console.error("Retry HLS error:", d));
                                } else {
                                    console.error("Cannot retry HLS load: HLS not supported or video ref missing.");
                                }
                            }, 1000); // Small delay before trying again
                            break;
                        case Hls.ErrorDetails.BUFFER_STALLED_ERROR:
                            // Attempt recovery
                            hls.recoverMediaError();
                            break;
                        default:
                            console.error("Unhandled fatal HLS error. Destroying Hls instance.");
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy();
                                hlsInstanceRef.current = null;
                            }
                            break;
                    }
                }
            });
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari on iOS/macOS)
            console.log("Native HLS support detected. Setting video source directly.");
            video.src = HLS_URL;
            // video.addEventListener('loadedmetadata', () => video.play()); // Auto-play if desired
        } else {
            console.error("HLS is not supported in this browser.");
        }

        // Cleanup function for useEffect: destroys Hls instance when component unmounts or streamId changes
        return () => {
            if (hlsInstanceRef.current) {
                hlsInstanceRef.current.destroy();
                hlsInstanceRef.current = null;
                console.log("Hls instance destroyed on component unmount.");
            }
        };
    }, [streamId]); // Dependency array: Effect re-runs when streamId changes

    return (
        <div>
            <video ref={videoRef} controls muted autoPlay playsInline style={{ width: '100%', maxWidth: '800px' }}></video>
        </div>
    );
};

export default HLSPlayer;