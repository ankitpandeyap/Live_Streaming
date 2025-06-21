import React, { useRef, useState, useEffect } from "react";
import "./App.css";
import HLSPlayer from "./components/HLSPlayer"; // Corrected path if needed, assuming 'components' folder

function App() {
  const videoRef = useRef(null);
  const localStreamRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const webSocketRef = useRef(null);

  const [isStreamingLocal, setIsStreamingLocal] = useState(false);
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [error, setError] = useState(null);
  const [streamId, setStreamId] = useState("");
  const [playingRecordedStreamId, setPlayingRecordedStreamId] = useState(null);

  // --- WebSocket URL (Adjust as per your backend) ---
  // In development, this will typically be different from your frontend URL.
  // Assuming your Spring Boot backend runs on localhost:8082
  const WEBSOCKET_URL = "ws://localhost:8082/live-stream";

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
      // 1. Generate a unique stream ID (or get one from backend)
      const id = `stream-${Date.now()}`;
      setStreamId(id);
      console.log(`Attempting to start broadcast with ID: ${id}`);

      // 2. Initialize WebSocket
      // THIS IS THE CRUCIAL LINE THAT WAS PREVIOUSLY INCORRECT
      webSocketRef.current = new WebSocket(`${WEBSOCKET_URL}/${id}`);

      webSocketRef.current.onopen = () => {
        console.log("WebSocket connected for stream:", id);
        // 3. Initialize MediaRecorder once WebSocket is open
        try {
          const options = {
            mimeType: "video/webm; codecs=vp8",
            timeslice: 500,
          }; // 500ms chunks

          if (!MediaRecorder.isTypeSupported(options.mimeType)) {
            console.warn(
              `${options.mimeType} is not supported, trying fallback...`
            );
            options.mimeType = "video/webm"; // Fallback
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
              webSocketRef.current &&
              webSocketRef.current.readyState === WebSocket.OPEN
            ) {
              webSocketRef.current.send(event.data); // Send Blob data over WebSocket
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
          console.log("MediaRecorder started, sending data to WebSocket.");
        } catch (mrError) {
          console.error("Error setting up MediaRecorder:", mrError);
          setError("Error setting up MediaRecorder: " + mrError.message);
          stopBroadcast(); // Clean up if MediaRecorder fails
        }
      };

      webSocketRef.current.onmessage = (event) => {
        console.log("WebSocket message received:", event.data);
      };

      webSocketRef.current.onclose = (event) => {
        console.log("WebSocket closed:", event);
        if (!event.wasClean) {
          setError(
            `WebSocket closed unexpectedly. Code: ${event.code}, Reason: ${event.reason}`
          );
        } else {
          setError(null); // Clear error if clean close
        }
        setIsBroadcasting(false); // Update broadcast status
        mediaRecorderRef.current = null; // Clear MediaRecorder ref
        webSocketRef.current = null; // Clear WebSocket ref
        setStreamId(""); // Clear streamId on close
      };

      webSocketRef.current.onerror = (err) => {
        console.error("WebSocket error:", err);
        setError("WebSocket error: Could not connect to streaming server.");
        stopBroadcast(); // Attempt to stop everything on error
      };
    } catch (wsError) {
      console.error("Error setting up WebSocket:", wsError);
      setError("Error setting up WebSocket: " + wsError.message);
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
      webSocketRef.current &&
      webSocketRef.current.readyState === WebSocket.OPEN
    ) {
      webSocketRef.current.close(1000, "User stopped broadcast"); // Clean close
    }
    setIsBroadcasting(false);
    setStreamId("");
  };

  // Cleanup effect for local stream when component unmounts
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
