// src/App.jsx

import React, { useRef, useState, useEffect } from "react";
import "./App.css";
import HLSPlayer from "./components/HLSPlayer"; // Corrected path if needed, assuming 'components' folder

function App() {
  const videoRef = useRef(null);
  const localStreamRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  // NEW: rawDataWebSocketRef will be for sending raw media data to Redis via a new backend endpoint
  const rawDataWebSocketRef = useRef(null); 

  const [isStreamingLocal, setIsStreamingLocal] = useState(false);
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [error, setError] = useState(null);
  const [streamId, setStreamId] = useState("");
  const [playingRecordedStreamId, setPlayingRecordedStreamId] = useState(null);

  // --- WebSocket URL for sending RAW MEDIA DATA to be published to Redis ---
  // We'll create a new endpoint in Spring Boot for this.
  const RAW_DATA_WEBSOCKET_URL = "ws://localhost:8082/raw-media-ingest"; // New endpoint

  const startLocalStream = async () => {
    setError(null);
    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720 }, // Request specific resolution for consistency
        audio: true,
      });

      localStreamRef.current = mediaStream; // Store stream in ref
      setIsStreamingLocal(true);

      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
        await videoRef.current
          .play()
          .catch((e) => console.error("Error playing video:", e));
      }
    } catch (err) {
      console.error("Error accessing media devices:", err);
      setError("Error accessing webcam: " + err.name + " - " + err.message);
      setIsStreamingLocal(false);
      localStreamRef.current = null;
    }
  };

  const stopLocalStream = () => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    setIsStreamingLocal(false);
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  };

  const startBroadcast = async () => {
    if (!localStreamRef.current) {
      await startLocalStream(); // Ensure local stream is running
      if (!localStreamRef.current) {
        setError("Failed to start local stream for broadcast.");
        return;
      }
    }

    if (isBroadcasting) {
      console.log("Already broadcasting.");
      return;
    }

    setError(null);
    try {
      // 1. Generate a unique stream ID
      const id = `stream-${Date.now()}`;
      setStreamId(id);
      console.log(`Attempting to start broadcast with ID: ${id}`);

      // 2. Initialize NEW WebSocket for raw data ingestion to Redis
      // This WebSocket URL will now include the stream ID for the backend to use as the Redis channel.
      rawDataWebSocketRef.current = new WebSocket(`${RAW_DATA_WEBSOCKET_URL}/${id}`);

      rawDataWebSocketRef.current.onopen = () => {
        console.log("Raw data WebSocket connected for stream:", id);
        // 3. Initialize MediaRecorder once WebSocket is open
        try {
          const options = {
            mimeType: "video/webm; codecs=vp8", // Use VP8 for broader browser support
            timeslice: 500, // Send data every 500ms
            videoBitsPerSecond: 1000000 // Target 1 Mbps for video bitrate
          }; 

          if (!MediaRecorder.isTypeSupported(options.mimeType)) {
            console.warn(
              `${options.mimeType} is not supported, trying fallback...`
            );
            options.mimeType = "video/webm"; // Fallback to generic webm
            if (!MediaRecorder.isTypeSupported(options.mimeType)) {
              console.error("No supported MIME type for MediaRecorder found.");
              setError(
                "Your browser does not support required video recording formats."
              );
              stopBroadcast();
              return;
            }
          }

          mediaRecorderRef.current = new MediaRecorder(
            localStreamRef.current,
            options
          );

          mediaRecorderRef.current.ondataavailable = (event) => {
            if (
              event.data.size > 0 &&
              rawDataWebSocketRef.current &&
              rawDataWebSocketRef.current.readyState === WebSocket.OPEN
            ) {
              // Send Blob data over the NEW WebSocket to be published to Redis
              rawDataWebSocketRef.current.send(event.data); 
            }
          };

          mediaRecorderRef.current.onstop = () => {
            console.log("MediaRecorder stopped.");
            // WebSocket will be handled by stopBroadcast
          };

          mediaRecorderRef.current.onerror = (event) => {
            console.error("MediaRecorder error:", event.error);
            setError(
              "MediaRecorder error: " +
                event.error.name +
                " - " +
                event.error.message
            );
            stopBroadcast();
          };

          mediaRecorderRef.current.start(options.timeslice); // Start recording with timeslice
          setIsBroadcasting(true);
          console.log("MediaRecorder started, sending data to Raw Data WebSocket.");
        } catch (mrError) {
          console.error("Error setting up MediaRecorder:", mrError);
          setError("Error setting up MediaRecorder: " + mrError.message);
          stopBroadcast(); // Clean up if MediaRecorder fails
        }
      };

      rawDataWebSocketRef.current.onmessage = (event) => {
        // This WebSocket is for sending, not typically receiving, but good to log.
        console.log("Raw data WebSocket message received:", event.data);
      };

      rawDataWebSocketRef.current.onclose = (event) => {
        console.log("Raw data WebSocket closed:", event);
        if (!event.wasClean) {
          setError(
            `Raw data WebSocket closed unexpectedly. Code: ${event.code}, Reason: ${event.reason}`
          );
        } else {
          setError(null); // Clear error if clean close
        }
        setIsBroadcasting(false); // Update broadcast status
        mediaRecorderRef.current = null; // Clear MediaRecorder ref
        rawDataWebSocketRef.current = null; // Clear rawDataWebSocketRef ref
        setStreamId(""); // Clear streamId on close
      };

      rawDataWebSocketRef.current.onerror = (err) => {
        console.error("Raw data WebSocket error:", err);
        setError("Raw data WebSocket error: Could not connect to ingestion server.");
        stopBroadcast(); // Attempt to stop everything on error
      };
    } catch (wsError) {
      console.error("Error setting up Raw data WebSocket:", wsError);
      setError("Error setting up Raw data WebSocket: " + wsError.message);
      setIsBroadcasting(false);
      setStreamId("");
    }
  };

  const stopBroadcast = () => {
    if (
      mediaRecorderRef.current &&
      mediaRecorderRef.current.state !== "inactive"
    ) {
      mediaRecorderRef.current.stop(); // This will trigger onstop event
    }
    if (
      rawDataWebSocketRef.current &&
      rawDataWebSocketRef.current.readyState === WebSocket.OPEN
    ) {
      rawDataWebSocketRef.current.close(1000, "User stopped broadcast"); // Clean close
    }
    setIsBroadcasting(false);
    setStreamId("");
  };

  // Cleanup effect for local stream and websockets when component unmounts
  useEffect(() => {
    return () => {
      stopBroadcast(); // Ensure broadcast is stopped
      stopLocalStream(); // Ensure local stream is stopped
    };
  }, []);

  const playRecordedStream = (id) => {
    setPlayingRecordedStreamId(id);
    setStreamId(""); // Ensure live stream isn't also playing if relevant
    setIsBroadcasting(false);
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>Live Stream Frontend</h1>
      </header>
      <main>
        <div className="video-container">
          {/* Display local webcam preview */}
          <div className="local-video-wrapper">
            <h2>Your Local Stream</h2>
            <video
              ref={videoRef}
              autoPlay
              muted
              playsInline
              className="local-video"
            ></video>
          </div>

          {error && <p className="error-message">{error}</p>}

          {/* Display HLS Player for the live stream */}
          {isBroadcasting && streamId && (
            <div className="hls-player-wrapper">
              <h2>Live HLS Stream (ID: {streamId})</h2>
              <HLSPlayer streamId={streamId} />
            </div>
          )}

          {/* Display HLS Player for a recorded stream */}
          {playingRecordedStreamId && (
            <div className="hls-player-wrapper">
              <h2>Recorded HLS Stream (ID: {playingRecordedStreamId})</h2>
              <HLSPlayer
                streamId={playingRecordedStreamId}
                isRecording={true}
              />
            </div>
          )}
        </div>
        <div className="controls">
          {!isBroadcasting ? (
            <>
              {!isStreamingLocal && (
                <button onClick={startLocalStream} disabled={isStreamingLocal}>
                  Start Local Webcam
                </button>
              )}
              {isStreamingLocal && (
                <button
                  onClick={startBroadcast}
                  disabled={!localStreamRef.current}
                >
                  Start Broadcast
                </button>
              )}
            </>
          ) : (
            <button onClick={stopBroadcast}>Stop Broadcast</button>
          )}
          {isStreamingLocal && !isBroadcasting && (
            <button onClick={stopLocalStream}>Stop Local Webcam</button>
          )}

          {/* New button to play a recorded stream */}
          <button onClick={() => playRecordedStream("your-test-recording-id")}>
            Play Recorded Stream (Test)
          </button>
          {playingRecordedStreamId && (
            <button onClick={() => setPlayingRecordedStreamId(null)}>
              Stop Recorded Playback
            </button>
          )}
        </div>
        <p className="status">
          Status:{" "}
          {isBroadcasting
            ? `Broadcasting (ID: ${streamId})`
            : isStreamingLocal
            ? "Local Webcam Active"
            : "Not Streaming"}
          {playingRecordedStreamId &&
            ` | Playing Recording (ID: ${playingRecordedStreamId})`}
        </p>
      </main>
    </div>
  );
}

export default App;