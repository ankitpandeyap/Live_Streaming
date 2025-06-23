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

    if (!streamId) {
        console.warn("HLSPlayer: streamId is undefined or empty, cannot load HLS source.");
        return (
            <div style={{ textAlign: 'center', color: '#ccc', padding: '20px' }}>
                <p>No stream ID available for playback.</p>
                <p>Please start a broadcast or enter a valid Stream ID.</p>
            </div>
        );
    }

    const BASE_URL = "http://localhost:8082";
    const HLS_URL = isRecording
        ? `${BASE_URL}/api/recordings/${streamId}/index.m3u8` // For recorded streams (protected)
        : `${BASE_URL}/hls/${streamId}/index.m3u8`;           // For live streams (current direct serving)

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        if (retryTimeoutRef.current) {
            clearTimeout(retryTimeoutRef.current);
            retryTimeoutRef.current = null;
        }

        if (hlsInstanceRef.current) {
            hlsInstanceRef.current.destroy();
            hlsInstanceRef.current = null;
            console.log("Destroying previous Hls instance for cleanup or stream source change.");
        }

        const initializeHls = () => {
            console.log("hls.js is supported. Initializing Hls instance for stream:", streamId, "URL:", HLS_URL);

            // HLS.js configuration for live streams:
            // For live streams, we want low latency and to stay close to the live edge.
            // These settings are more impactful with ABR (Phase 2.2) but useful even now.
            const hlsConfig = {
                // For live streams, force the player to stay closer to the live edge.
                // This means less buffering ahead, but potentially lower latency.
                // This value (e.g., 0.5 to 1.5 seconds) makes the player try to buffer
                // only a small amount ahead of the current live position.
                // For live streams, `hls.js` generally handles seekable range
                // based on the manifest. If FFmpeg produces a short live window,
                // the UI seek bar will reflect that automatically.
                liveSyncDuration: isRecording ? undefined : 1.5, // Target 1.5s latency for live, unlimited for VOD
                // You can also consider `liveMaxLatency` and `liveDurationInfinity`
                // if you need very specific live-edge behavior, but often
                // `liveSyncDuration` is a good starting point.
                // For low latency:
                // lowLatencyMode: !isRecording, // Experimental, but can reduce latency further
                // maxLiveSyncPlaybackRate: 1.1, // Allow faster playback to catch up to live edge
            };

            const hls = new Hls(hlsConfig);
            hlsInstanceRef.current = hls;

            hls.attachMedia(video);

            hls.on(Hls.Events.MANIFEST_PARSED, function (event, data) {
                console.log('HLS Manifest Parsed:', data);
                // For live streams, ensure we jump to the live edge upon manifest parsing.
                // hls.js usually does this by default if the manifest indicates a live stream.
                if (!isRecording && hls.live) {
                    console.log("HLS is live, attempting to seek to live edge.");
                    // Check if currentTime is significantly behind the live edge.
                    // This might be useful if playback starts from a cached position.
                    // A simple video.currentTime = video.duration is often enough for live.
                    if (video.duration - video.currentTime > 5) { // If more than 5 seconds behind live
                         video.currentTime = video.duration; // Jump to live edge
                    }
                }
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
                                if (Hls.isSupported() && videoRef.current) {
                                    console.log("Attempting to re-initialize Hls instance after error.");
                                    initializeHls();
                                } else {
                                    console.error("Cannot retry HLS load: HLS not supported or video ref missing.");
                                }
                            }, isRecording ? 5000 : 1000); // Shorter retry for live, longer for recorded (might be truly gone)
                            break;
                        case Hls.ErrorDetails.BUFFER_STALLED_ERROR:
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

            hls.loadSource(HLS_URL);
            console.log(`Loading HLS source: ${HLS_URL}`);
        };

        const checkAndLoadHlsManifest = (retries = 0) => {
            const MAX_MANIFEST_RETRIES = 10;
            fetch(HLS_URL, { method: 'HEAD' })
                .then(response => {
                    if (response.ok) {
                        console.log(`HLS manifest found for ${streamId}. Proceeding with HLS initialization.`);
                        initializeHls();
                    } else if (response.status === 404 && retries < MAX_MANIFEST_RETRIES) {
                        console.log(`HLS manifest for ${streamId} not yet available (404), retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 2000);
                    } else {
                        console.error(`Failed to load HLS manifest for ${streamId}: Status ${response.status}. Giving up after ${retries} attempts.`);
                    }
                })
                .catch(error => {
                    if (retries < MAX_MANIFEST_RETRIES) {
                        console.error(`Network error checking HLS manifest for ${streamId}:`, error, `Retrying... (Attempt ${retries + 1}/${MAX_MANIFEST_RETRIES})`);
                        retryTimeoutRef.current = setTimeout(() => checkAndLoadHlsManifest(retries + 1), 3000);
                    } else {
                        console.error(`Network error checking HLS manifest for ${streamId}:`, error, `Giving up after ${retries} attempts.`);
                    }
                });
        };

        if (Hls.isSupported()) {
            if (isRecording) {
                console.log(`Attempting to load recorded HLS stream immediately: ${HLS_URL}`);
                initializeHls();
            } else {
                checkAndLoadHlsManifest();
            }
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            console.log("Native HLS support detected. Setting video source directly.");
            video.src = HLS_URL;
            video.addEventListener('loadedmetadata', () => {
                // For native HLS, if it's a live stream, ensure it jumps to the end (live edge)
                if (!isRecording && video.duration && video.duration > 0) {
                    video.currentTime = video.duration;
                    console.log("Native HLS: Jumped to live edge.");
                }
                video.play().catch(error => {
                    console.warn("Native HLS autoplay prevented:", error);
                });
            }, { once: true });
        } else {
            console.error("HLS is not supported in this browser.");
        }

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
    }, [streamId, isRecording, HLS_URL]);

    return (
        <div>
            <video
                ref={videoRef}
                controls
                muted
                autoPlay
                playsInline
                style={{ width: '100%', maxWidth: '800px' }}
            ></video>
        </div>
    );
};

export default HLSPlayer;