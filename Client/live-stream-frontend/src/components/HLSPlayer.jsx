import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';

/**
 * A React component for playing HLS (HTTP Live Streaming) videos.
 * It supports both live streams and recorded streams.
 *
 * @param {object} props - The component props.
 * @param {string} props.streamId - The unique ID of the stream to play.
 * @param {boolean} [props.isRecording=false] - True if playing a recorded stream, false for a live stream.
 */
const HLSPlayer = ({ streamId, isRecording = false }) => {
    const videoRef = useRef(null);
    const hlsInstanceRef = useRef(null);
    const retryTimeoutRef = useRef(null); // To store the timeout ID for cleanup

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

    // Dynamically choose the URL based on the 'isRecording' prop
    const BASE_URL = "http://localhost:8082";
    const HLS_URL = isRecording
        ? `${BASE_URL}/api/recordings/${streamId}/index.m3u8` // For recorded streams (protected)
        : `${BASE_URL}/hls/${streamId}/index.m3u8`;           // For live streams (current direct serving)

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        // Clear any pending retries from previous renders/unmounts
        if (retryTimeoutRef.current) {
            clearTimeout(retryTimeoutRef.current);
            retryTimeoutRef.current = null;
        }

        // Destroy existing Hls instance if any, especially on streamId/isRecording change
        if (hlsInstanceRef.current) {
            hlsInstanceRef.current.destroy();
            hlsInstanceRef.current = null;
            console.log("Destroying previous Hls instance for cleanup or stream source change.");
        }

        const initializeHls = () => {
            console.log("hls.js is supported. Initializing Hls instance for stream:", streamId, "URL:", HLS_URL);
            const hls = new Hls();
            hlsInstanceRef.current = hls;

            hls.attachMedia(video);

            hls.on(Hls.Events.MANIFEST_PARSED, function (event, data) {
                console.log('HLS Manifest Parsed:', data);
                // Attempt to play the video.
                // Because 'muted' and 'autoplay' are on the <video> tag,
                // this `play()` call should succeed in most modern browsers.
                video.play().catch(error => {
                    console.warn("Video playback was prevented (likely by browser autoplay policy if not muted, or user settings):", error);
                });
            });

            hls.on(Hls.Events.ERROR, function (event, data) {
                console.error('Fatal HLS.js error:', data);
                if (data.fatal) {
                    switch (data.details) {
                        case Hls.ErrorDetails.MANIFEST_LOAD_ERROR:
                        case Hls.ErrorDetails.MANIFEST_PARSE_ERROR:
                        case Hls.ErrorDetails.LEVEL_LOAD_ERROR:
                        case Hls.ErrorDetails.FRAG_LOAD_ERROR:
                            console.warn("Retrying HLS load due to fatal network/parse error:", data.details);
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy(); // Destroy current instance
                                hlsInstanceRef.current = null;
                            }
                            retryTimeoutRef.current = setTimeout(() => {
                                // Re-attempt the entire HLS initialization process
                                if (Hls.isSupported() && videoRef.current) {
                                    console.log("Attempting to re-initialize Hls instance after error.");
                                    initializeHls();
                                } else {
                                    console.error("Cannot retry HLS load: HLS not supported or video ref missing.");
                                }
                            }, 1000); // Small delay before trying again
                            break;
                        case Hls.ErrorDetails.BUFFER_STALLED_ERROR:
                            // Attempt to recover from stalling; FFmpeg frame drops might still cause this.
                            console.warn("HLS buffer stalled, attempting to recover...");
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

            // Load the source after attaching media and setting up event listeners
            hls.loadSource(HLS_URL);
            console.log(`Loading HLS source: ${HLS_URL}`);
        };

        const checkAndLoadHlsManifest = (retries = 0) => {
            const MAX_MANIFEST_RETRIES = 10; // Max attempts for initial manifest fetch for live streams
            fetch(HLS_URL, { method: 'HEAD' }) // HEAD request is lighter
                .then(response => {
                    if (response.ok) {
                        console.log(`HLS manifest found for ${streamId}. Proceeding with HLS initialization.`);
                        initializeHls();
                    } else if (response.status === 404 && retries < MAX_MANIFEST_RETRIES) {
                        console.log(`HLS manifest for ${streamId} not yet available (404), retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 2000); // Retry after 2 seconds
                    } else {
                        console.error(`Failed to load HLS manifest for ${streamId}: Status ${response.status}. Giving up after ${retries} attempts.`);
                        // Optionally display an error message to the user here
                    }
                })
                .catch(error => {
                    if (retries < MAX_MANIFEST_RETRIES) {
                        console.error(`Network error checking HLS manifest for ${streamId}:`, error, `Retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 3000); // Retry after 3 seconds on network error
                    } else {
                         console.error(`Network error checking HLS manifest for ${streamId}:`, error, `Giving up after ${retries} attempts.`);
                         // Optionally display an error message to the user here
                    }
                });
        };

        if (Hls.isSupported()) {
            // For live streams, check if the manifest is available first.
            // For recordings, assume it's available or handle errors immediately.
            if (isRecording) {
                console.log(`Attempting to load recorded HLS stream immediately: ${HLS_URL}`);
                initializeHls(); // Load directly for recordings
            } else {
                checkAndLoadHlsManifest(); // Start the check/load process for live streams
            }
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari on iOS/macOS)
            console.log("Native HLS support detected. Setting video source directly.");
            video.src = HLS_URL;
            // Attempt to play on metadata load, ensuring `muted` and `autoplay` are also set on the <video> tag
            video.addEventListener('loadedmetadata', () => {
                video.play().catch(error => {
                    console.warn("Native HLS autoplay prevented:", error);
                });
            }, { once: true }); // Play once on metadata load
        } else {
            console.error("HLS is not supported in this browser.");
            // Optionally display a user-friendly message for unsupported browsers
        }

        // Cleanup function for useEffect: destroys Hls instance and clears timeouts
        return () => {
            if (hlsInstanceRef.current) {
                hlsInstanceRef.current.destroy();
                hlsInstanceRef.current = null;
                console.log('Hls instance destroyed on component unmount.');
            }
            if (retryTimeoutRef.current) {
                clearTimeout(retryTimeoutRef.current);
                retryTimeoutRef.current = null;
            }
        };
    }, [streamId, isRecording, HLS_URL]); // Dependency array: Effect re-runs when streamId or isRecording changes

    return (
        <div>
            <video
                ref={videoRef}
                controls
                muted      // Crucially, start muted to allow autoplay without user interaction
                autoPlay   // Tells the browser to attempt playing immediately
                playsInline // Recommended for mobile browsers to play within the element
                style={{ width: '100%', maxWidth: '800px' }}
            ></video>
        </div>
    );
};

export default HLSPlayer;