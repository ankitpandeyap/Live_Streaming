import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';

/**
 * A React component for playing HLS (HTTP Live Streaming) videos.
 * This component is now dedicated to playing LIVE streams.
 *
 * @param {object} props - The component props.
 * @param {string} props.streamId - The unique ID of the LIVE stream to play.
 */
const HLSPlayer = ({ streamId }) => {
    const videoRef = useRef(null);
    const hlsInstanceRef = useRef(null);
    const retryTimeoutRef = useRef(null); // To store the timeout ID for cleanup

    // If no streamId, display a message
    if (!streamId) {
        console.warn("HLSPlayer: streamId is undefined or empty for live stream, cannot load HLS source.");
        return (
            <div style={{ textAlign: 'center', color: '#ccc', padding: '20px' }}>
                <p>No live stream ID available for playback.</p>
                <p>Please start a broadcast.</p>
            </div>
        );
    }

    const BASE_URL = "http://localhost:8082";
    // HLS URL for live streams (backend will serve this from FFmpeg output directory)
    const HLS_URL = `${BASE_URL}/hls/${streamId}/index.m3u8`;

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        // Clear any pending retry timeouts
        if (retryTimeoutRef.current) {
            clearTimeout(retryTimeoutRef.current);
            retryTimeoutRef.current = null;
        }

        // Destroy any existing Hls instance before initializing a new one
        if (hlsInstanceRef.current) {
            hlsInstanceRef.current.destroy();
            hlsInstanceRef.current = null;
            console.log("Destroying previous Hls instance for new live stream.");
        }

        const initializeHls = () => {
            console.log("hls.js is supported. Initializing Hls instance for LIVE stream:", streamId, "URL:", HLS_URL);

            // HLS.js configuration for live streams: prioritize low latency
            const hlsConfig = {
                liveSyncDuration: 1.5, // Target 1.5s latency (stay close to live edge)
                liveMaxLatencyDuration: 3, // Max 3s latency allowed
                lowLatencyMode: true, // Enable experimental low latency features
                maxLiveSyncPlaybackRate: 1.1, // Allow faster playback to catch up to live edge
                // Other options like 'debug: true' can be added for more verbose logging
            };

            const hls = new Hls(hlsConfig);
            hlsInstanceRef.current = hls;

            hls.attachMedia(video);

            hls.on(Hls.Events.MANIFEST_PARSED, function (event, data) {
                console.log('HLS Manifest Parsed:', data);
                // For live streams, ensure we jump to the live edge upon manifest parsing.
                // hls.js usually handles this well with `liveSyncDuration`.
                if (hls.live && video.duration - video.currentTime > 5) { // If more than 5 seconds behind live
                    console.log("HLS is live, attempting to seek to live edge.");
                    video.currentTime = video.duration; // Jump to live edge
                }
                video.play().catch(error => {
                    console.warn("Video playback was prevented (likely autoplay policy):", error);
                });
            });

            hls.on(Hls.Events.ERROR, function (event, data) {
                console.error('Fatal HLS.js error for LIVE stream:', data);
                if (data.fatal) {
                    switch (data.details) {
                        case Hls.ErrorDetails.MANIFEST_LOAD_ERROR:
                        case Hls.ErrorDetails.MANIFEST_PARSE_ERROR:
                        case Hls.ErrorDetails.LEVEL_LOAD_ERROR:
                        case Hls.ErrorDetails.FRAG_LOAD_ERROR:
                            console.warn("Retrying HLS live stream load due to fatal network/parse error:", data.details);
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy(); // Destroy current instance
                                hlsInstanceRef.current = null;
                            }
                            retryTimeoutRef.current = setTimeout(() => {
                                if (Hls.isSupported() && videoRef.current) {
                                    console.log("Attempting to re-initialize Hls instance after error.");
                                    initializeHls();
                                } else {
                                    console.error("Cannot retry HLS load: HLS not supported or video ref missing.");
                                }
                            }, 1000); // Shorter retry for live streams
                            break;
                        case Hls.ErrorDetails.BUFFER_STALLED_ERROR:
                            console.warn("HLS live stream buffer stalled, attempting to recover...");
                            hls.recoverMediaError();
                            break;
                        default:
                            console.error("Unhandled fatal HLS error for LIVE stream. Destroying Hls instance.");
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy();
                                hlsInstanceRef.current = null;
                            }
                            break;
                    }
                }
            });

            hls.loadSource(HLS_URL);
            console.log(`Loading HLS source for LIVE stream: ${HLS_URL}`);
        };

        // Check for manifest availability before trying to load HLS.js
        const checkAndLoadHlsManifest = (retries = 0) => {
            const MAX_MANIFEST_RETRIES = 20; // Increased retries for live stream manifest to become available
            fetch(HLS_URL, { method: 'HEAD' })
                .then(response => {
                    if (response.ok) {
                        console.log(`HLS manifest found for live stream ${streamId}. Proceeding with HLS initialization.`);
                        initializeHls();
                    } else if (response.status === 404 && retries < MAX_MANIFEST_RETRIES) {
                        console.log(`HLS manifest for live stream ${streamId} not yet available (404), retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 1000); // Check every 1 second
                    } else {
                        console.error(`Failed to load HLS manifest for live stream ${streamId}: Status ${response.status}. Giving up after ${retries} attempts.`);
                        setError("Live stream manifest not available or failed to load."); // Set error in UI
                    }
                })
                .catch(error => {
                    if (retries < MAX_MANIFEST_RETRIES) {
                        console.error(`Network error checking HLS manifest for live stream ${streamId}:`, error, `Retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 2000); // Retry every 2 seconds on network error
                    } else {
                        console.error(`Network error checking HLS manifest for live stream ${streamId}:`, error, `Giving up after ${retries} attempts.`);
                        setError("Network error: Could not connect to live stream server."); // Set error in UI
                    }
                });
        };

        // Main logic for component mount/update
        if (Hls.isSupported()) {
            checkAndLoadHlsManifest(); // Start checking for live manifest
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (e.g., Safari)
            console.log("Native HLS support detected for live stream. Setting video source directly.");
            video.src = HLS_URL;
            video.addEventListener('loadedmetadata', () => {
                if (video.duration && video.duration > 0) {
                    video.currentTime = video.duration; // Jump to live edge
                    console.log("Native HLS: Jumped to live edge.");
                }
                video.play().catch(error => {
                    console.warn("Native HLS autoplay prevented:", error);
                });
            }, { once: true });
        } else {
            console.error("HLS is not supported in this browser for live streams.");
            setError("Your browser does not support HLS streaming.");
        }

        // Cleanup function for component unmount or streamId change
        return () => {
            if (hlsInstanceRef.current) {
                hlsInstanceRef.current.destroy();
                hlsInstanceRef.current = null;
                console.log('Hls instance destroyed on component unmount or streamId change.');
            }
            if (retryTimeoutRef.current) {
                clearTimeout(retryTimeoutRef.current);
                retryTimeoutRef.current = null;
            }
            // Ensure video source is cleared when unmounting
            if (video) {
                video.src = '';
                video.load();
            }
        };
    }, [streamId, HLS_URL]); // Re-run effect if streamId or HLS_URL changes

    return (
        <div>
            {/* Display video player */}
            <video
                ref={videoRef}
                controls // Show controls for user interaction
                muted // Mute by default to help with autoplay policies
                autoPlay // Attempt autoplay
                playsInline // Important for iOS inline playback
                style={{ width: '100%', maxWidth: '800px' }} // Styling
            ></video>
            {/* You could add a loading spinner or error message here */}
            {/* {error && <p style={{color: 'red'}}>{error}</p>} */}
        </div>
    );
};

export default HLSPlayer;